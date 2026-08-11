package io.github.markpollack.prreview.v3;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
import io.github.markpollack.workflow.flows.v3.JuryProvider;
import io.github.markpollack.workflow.flows.v3.Step;
import io.github.markpollack.workflow.spec.v3.ContentDigest;
import io.github.markpollack.workflow.spec.v3.ProviderDeclaration;
import io.github.markpollack.workflow.spec.v3.WireJson;
import io.github.markpollack.workflow.spec.v3.WorkflowSpec;
import io.github.markpollack.workflow.spec.v3.envelope.ArtifactLocator;
import io.github.markpollack.workflow.spec.v3.envelope.CarriedSchema;
import io.github.markpollack.workflow.spec.v3.envelope.CuratedCatalog;
import io.github.markpollack.workflow.spec.v3.envelope.DirectProviderBinding;
import io.github.markpollack.workflow.spec.v3.envelope.ProviderContract;
import io.github.markpollack.workflow.spec.v3.envelope.ProviderKind;
import io.github.markpollack.workflow.spec.v3.envelope.SelectedProvider;

/** Builds mutable discovery data from the workflow and the providers in this app. */
final class CuratedCatalogFactory {

	private static final String TYPE_NAMESPACE = "https://schemas.agentworks.dev/types/";

	private static final Set<Class<?>> INTEGERS = Set.of(Byte.class, Short.class, Integer.class, Long.class);

	private static final Set<Class<?>> NUMBERS = Set.of(Float.class, Double.class);

	private static final Map<Class<?>, Class<?>> BOXED = Map.of(boolean.class, Boolean.class, byte.class, Byte.class,
			char.class, Character.class, short.class, Short.class, int.class, Integer.class, long.class, Long.class,
			float.class, Float.class, double.class, Double.class);

	private CuratedCatalogFactory() {
	}

	static CuratedCatalog from(WorkflowSpec spec, Map<String, Step<?, ?>> leaves, Map<String, JuryProvider> juries) {
		JsonNode document = WireJson.mapper().valueToTree(spec);
		Map<String, Set<String>> dispatchKeys = dispatchKeys(document);
		List<SelectedProvider> entries = new ArrayList<>();

		spec.providers().forEach((alias, declaration) -> {
			JuryProvider jury = juries.get(alias);
			if (jury != null) {
				entries.add(juryEntry(declaration, jury));
				return;
			}
			Step<?, ?> leaf = leaves.get(alias);
			if (leaf == null) {
				throw new IllegalStateException("the workflow declares provider '" + alias
						+ "' and this deployment supplied no matching step or jury");
			}
			Class<?>[] io = declaredTypes(leaf);
			ProviderContract contract = new ProviderContract(declaration.contract().name(),
					declaration.contract().version(), ProviderKind.OPERATION, humanize(alias), describe(leaf),
					carried(inputSchema(io[0], dispatchKeys.getOrDefault(alias, Set.of())), requestTypeName(alias)),
					carried(schemaOf(io[1], new LinkedHashSet<>()), io[1].getSimpleName()), null);
			entries.add(new SelectedProvider(contract,
					new DirectProviderBinding("java", artifact(leaf.getClass()), leaf.getClass().getName())));
		});
		entries.sort(Comparator.comparing((SelectedProvider entry) -> entry.contract().name())
			.thenComparingInt(entry -> entry.contract().version()));

		String specHash = ContentDigest.sha256(spec);
		return new CuratedCatalog(WorkflowSpec.API_VERSION, CuratedCatalog.KIND,
				List.of(new CuratedCatalog.WorkflowCandidate(spec.metadata().name(), specHash, spec)), entries,
				List.of());
	}

	private static SelectedProvider juryEntry(ProviderDeclaration declaration, JuryProvider jury) {
		JsonNode input = WireJson.mapper().createObjectNode().put("type", "object");
		JsonNode output = WireJson.mapper().createObjectNode().put("type", "object");
		ProviderContract contract = new ProviderContract(declaration.contract().name(),
				declaration.contract().version(), ProviderKind.JURY, humanize(jury.alias()), null,
				carried(input, requestTypeName(jury.alias())), carried(output, "Verdict"), null);
		return new SelectedProvider(contract,
				new DirectProviderBinding("java", artifact(PrReviewV3Config.class), "buildHealthJury"));
	}

	private static ArtifactLocator artifact(Class<?> type) {
		String resource = "/" + type.getName().replace('.', '/') + ".class";
		try (InputStream input = type.getResourceAsStream(resource)) {
			if (input == null) {
				throw new IllegalStateException("cannot resolve provider class " + resource);
			}
			String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
			return new ArtifactLocator("classpath", resource,
					new ArtifactLocator.RawArtifactDigest("raw-file/v1", "sha256", digest));
		}
		catch (IOException ex) {
			throw new IllegalStateException("cannot read provider class " + resource, ex);
		}
		catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

	private static Map<String, Set<String>> dispatchKeys(JsonNode document) {
		Map<String, Set<String>> keys = new TreeMap<>();
		collectDispatchKeys(document, keys);
		return keys;
	}

	private static void collectDispatchKeys(JsonNode node, Map<String, Set<String>> keys) {
		if (node.isObject() && node.hasNonNull("operation") && node.get("operation").isTextual()) {
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

	private static Class<?>[] declaredTypes(Step<?, ?> leaf) {
		for (Type declared : leaf.getClass().getGenericInterfaces()) {
			if (declared instanceof ParameterizedType parameterized && parameterized.getRawType() == Step.class) {
				return new Class<?>[] { raw(parameterized.getActualTypeArguments()[0]),
						raw(parameterized.getActualTypeArguments()[1]) };
			}
		}
		throw new IllegalStateException(leaf.getClass().getName() + " does not declare Step<I,O> directly");
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

	private static CarriedSchema carried(JsonNode schema, String typeName) {
		return new CarriedSchema(TYPE_NAMESPACE + typeName, ContentDigest.sha256(schema), schema);
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
