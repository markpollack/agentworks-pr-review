package io.github.markpollack.prreview.v3;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.markpollack.workflow.core.Description;
import io.github.markpollack.workflow.flows.v3.Step;
import io.github.markpollack.workflow.spec.CanonicalJson;
import io.github.markpollack.workflow.spec.v3.WireJson;
import io.github.markpollack.workflow.spec.v3.WorkflowSpec;
import io.github.markpollack.workflow.spec.v3.envelope.CarriedSchema;
import io.github.markpollack.workflow.spec.v3.envelope.CatalogEntry;
import io.github.markpollack.workflow.spec.v3.envelope.OperationCatalog;

/**
 * Builds this deployment's {@link OperationCatalog} (CONTRACT §13.2) from the same
 * declarations the workflow was derived from: the {@code operations} block of the emitted
 * spec says which refs this deployment must describe, each node's {@code input} map says
 * what a dispatch is named, and each leaf's {@code implements Step<I,O>} says what those
 * names are typed.
 *
 * <p>
 * <b>Why this is derived and not written.</b> A hand-written catalog is a second copy of
 * every operation's I/O shape, kept beside a first copy that javac checks — so it is
 * correct exactly until someone renames a record component, and it fails by serving a
 * consumer the wrong form to render. The previous hand-authored catalog had already gone
 * wrong that way: it described {@code cleanup-branch} as taking a {@code BuildResult} and
 * listed a {@code fix-and-retest} operation that this workflow no longer has.
 *
 * <p>
 * <b>The duplication that remains, and is filed rather than hidden.</b> Mapping a Java
 * type to a JSON Schema is {@code flows.v3.Schemas}' job, and it is package-private, so
 * {@link #schemaOf} below is a second implementation of §7.4's type→schema rule living in
 * a consumer. It agrees with the first today and nothing makes it keep agreeing. The
 * right answer is public API on the authoring surface — a deployment needs the same
 * derivation the spec's {@code inputSchema} already uses — and that is a Step-2.4
 * finding, not something to change here on the way past.
 */
final class OperationCatalogFactory {

	/**
	 * The capture identity. An instance id names a snapshot of what a deployment deploys,
	 * so a content change means a new id and a new {@code capturedAt} — both are authored
	 * here deliberately and neither is a function of the clock.
	 */
	static final String INSTANCE_ID = "agentworks-pr-review-app@2026-07-28.1";

	static final String CAPTURED_AT = "2026-07-28T12:00:00.000Z";

	private static final String TYPE_NAMESPACE = "https://schemas.agentworks.dev/types/";

	private static final String OPERATION_VERSION = "1.0.0";

	private static final Set<Class<?>> INTEGERS = Set.of(Byte.class, Short.class, Integer.class, Long.class);

	private static final Set<Class<?>> NUMBERS = Set.of(Float.class, Double.class);

	private static final Map<Class<?>, Class<?>> BOXED = Map.of(boolean.class, Boolean.class, byte.class, Byte.class,
			char.class, Character.class, short.class, Short.class, int.class, Integer.class, long.class, Long.class,
			float.class, Float.class, double.class, Double.class);

	private OperationCatalogFactory() {
	}

	/**
	 * @param spec the emitted workflow — the authority on which refs exist and on what
	 * each dispatch site names
	 * @param leaves the deployed operation, by the alias the spec knows it under
	 */
	static OperationCatalog from(WorkflowSpec spec, Map<String, Step<?, ?>> leaves) {
		JsonNode document = WireJson.mapper().valueToTree(spec);
		Map<String, Set<String>> dispatchKeys = dispatchKeys(document);

		List<CatalogEntry> entries = new ArrayList<>();
		spec.operations().forEach((alias, declaration) -> {
			Step<?, ?> leaf = leaves.get(alias);
			if (leaf == null) {
				throw new IllegalStateException("the spec declares operation '" + alias
						+ "' and this deployment supplied no leaf for it — a catalog that omits it would tell a"
						+ " consumer the operation is not deployed, which would be false");
			}
			Class<?>[] io = declaredTypes(leaf);
			entries.add(new CatalogEntry(declaration.ref(), OPERATION_VERSION, humanize(alias), describe(leaf),
					carried(inputSchema(io[0], dispatchKeys.getOrDefault(alias, Set.of())), requestTypeName(alias)),
					carried(schemaOf(io[1], new LinkedHashSet<>()), io[1].getSimpleName()), null, null));
		});
		entries.sort((left, right) -> left.ref().compareTo(right.ref()));

		return new OperationCatalog(WorkflowSpec.API_VERSION, OperationCatalog.KIND,
				new OperationCatalog.CatalogInstance(INSTANCE_ID, CAPTURED_AT), entries);
	}

	// ── what a dispatch is named ─────────────────────────────────────────

	/**
	 * The CD-21 parameter names each operation is dispatched under, read off the emitted
	 * spec rather than re-derived from the leaf's type. Whether a value binds whole or by
	 * its components is a fact about the <em>graph</em> — the entrypoint binds whole
	 * where the same record would otherwise be read as a parameter list — so the spec is
	 * the only place that already knows.
	 */
	private static Map<String, Set<String>> dispatchKeys(JsonNode document) {
		Map<String, Set<String>> keys = new TreeMap<>();
		collectDispatchKeys(document, keys);
		return keys;
	}

	private static void collectDispatchKeys(JsonNode node, Map<String, Set<String>> keys) {
		if (node.isObject() && node.hasNonNull("operation") && node.get("operation").isTextual()) {
			// Sorted, not insertion-ordered: the spec's input map is a Map.copyOf, whose
			// iteration order is JVM-salt randomized, and `required` is a JSON array that
			// canonicalization does not reorder — an unsorted key set would emit a
			// different (equally valid) catalog on every run.
			Set<String> names = keys.computeIfAbsent(node.get("operation").asText(), alias -> new TreeSet<>());
			JsonNode input = node.get("input");
			if (input != null && input.isObject()) {
				input.fieldNames().forEachRemaining(names::add);
			}
		}
		node.forEach(child -> collectDispatchKeys(child, keys));
	}

	private static JsonNode inputSchema(Class<?> inputType, Set<String> keys) {
		ObjectNode schema = WireJson.mapper().createObjectNode();
		schema.put("type", "object");
		schema.put("additionalProperties", false);
		ObjectNode properties = schema.putObject("properties");
		var required = schema.putArray("required");
		Map<String, Class<?>> components = componentsOf(inputType);
		for (String key : keys) {
			Class<?> memberType = components.getOrDefault(key, inputType);
			properties.set(key, schemaOf(memberType, new LinkedHashSet<>()));
			required.add(key);
		}
		return schema;
	}

	// ── what those names are typed ───────────────────────────────────────

	/** The {@code I} and {@code O} of {@code implements Step<I,O>}. */
	private static Class<?>[] declaredTypes(Step<?, ?> leaf) {
		for (Type declared : leaf.getClass().getGenericInterfaces()) {
			if (declared instanceof ParameterizedType parameterized && parameterized.getRawType() == Step.class) {
				return new Class<?>[] { raw(parameterized.getActualTypeArguments()[0]),
						raw(parameterized.getActualTypeArguments()[1]) };
			}
		}
		throw new IllegalStateException(leaf.getClass().getName() + " does not declare Step<I,O> directly, so this"
				+ " deployment cannot say what it dispatches — name the types on the implements clause");
	}

	private static Map<String, Class<?>> componentsOf(Class<?> type) {
		if (!type.isRecord()) {
			return Map.of();
		}
		Map<String, Class<?>> components = new TreeMap<>();
		for (RecordComponent component : type.getRecordComponents()) {
			components.put(component.getName(), boxed(component.getType()));
		}
		return components;
	}

	/**
	 * A member's schema. Deliberately shape-only: a record says {@code int pr}, it does
	 * not say {@code minimum: 1}, and inventing the constraint would be the catalog
	 * making a claim the code never made.
	 */
	private static JsonNode schemaOf(Class<?> type, Set<Class<?>> enclosing) {
		ObjectNode schema = WireJson.mapper().createObjectNode();
		Class<?> resolved = boxed(type);
		if (resolved == Boolean.class) {
			schema.put("type", "boolean");
		}
		else if (INTEGERS.contains(resolved)) {
			schema.put("type", "integer");
		}
		else if (NUMBERS.contains(resolved)) {
			schema.put("type", "number");
		}
		else if (resolved == Character.class || CharSequence.class.isAssignableFrom(resolved) || resolved.isEnum()) {
			schema.put("type", "string");
		}
		else if (resolved.isArray() || Collection.class.isAssignableFrom(resolved)) {
			schema.put("type", "array");
		}
		else if (Map.class.isAssignableFrom(resolved)) {
			schema.put("type", "object");
		}
		else if (resolved.isRecord() && enclosing.add(resolved)) {
			ObjectNode nested = WireJson.mapper().createObjectNode();
			nested.put("type", "object");
			nested.put("additionalProperties", false);
			ObjectNode properties = nested.putObject("properties");
			var required = nested.putArray("required");
			for (RecordComponent component : resolved.getRecordComponents()) {
				properties.set(component.getName(), schemaOf(component.getType(), enclosing));
				required.add(component.getName());
			}
			enclosing.remove(resolved);
			return nested;
		}
		return schema;
	}

	// ── identity ─────────────────────────────────────────────────────────

	private static CarriedSchema carried(JsonNode schema, String typeName) {
		return new CarriedSchema(TYPE_NAMESPACE + typeName, digest(schema), schema);
	}

	/**
	 * {@code sha256:<hex>} over the RFC 8785 canonical bytes of the schema (C-6/C-11).
	 */
	private static String digest(JsonNode schema) {
		try {
			byte[] canonical = CanonicalJson.canonicalize(WireJson.mapper().writeValueAsBytes(schema));
			return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
		}
		catch (Exception ex) {
			throw new IllegalStateException("cannot digest a derived schema", ex);
		}
	}

	private static String requestTypeName(String alias) {
		StringBuilder name = new StringBuilder();
		for (String word : alias.split("-")) {
			name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return name.append("Request").toString();
	}

	private static String humanize(String alias) {
		String spaced = alias.replace('-', ' ');
		return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1).toLowerCase(Locale.ROOT);
	}

	private static String describe(Step<?, ?> leaf) {
		Description description = leaf.getClass().getAnnotation(Description.class);
		return description == null ? null : description.value();
	}

	private static Class<?> raw(Type type) {
		if (type instanceof Class<?> clazz) {
			return clazz;
		}
		if (type instanceof ParameterizedType parameterized) {
			return raw(parameterized.getRawType());
		}
		throw new IllegalStateException("cannot resolve " + type + " to a class");
	}

	private static Class<?> boxed(Class<?> type) {
		Class<?> box = BOXED.get(type);
		return box != null ? box : type;
	}

}
