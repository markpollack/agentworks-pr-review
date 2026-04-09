#!/usr/bin/env bash
#
# Renames Java package from com.tuvium.prreview to io.github.markpollack.prreview.
#
# What it does:
#   1. Moves source directories (main + test)
#   2. Updates all Java files: package declarations and import statements
#   3. Updates pom.xml groupId and main class reference
#   4. Updates application.yml @ConfigurationProperties scan base
#   5. Updates ArchitectureTest package references
#   6. Updates CLAUDE.md and plans/ references
#   7. Cleans up empty old directories
#
# Usage:
#   cd /home/mark/projects/agentworks-pr-review
#   bash scripts/rename-package.sh
#
# Safe to re-run — idempotent (checks if already renamed).

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

OLD_PKG="com.tuvium.prreview"
NEW_PKG="io.github.markpollack.prreview"
OLD_DIR="com/tuvium/prreview"
NEW_DIR="io/github/markpollack/prreview"
OLD_GROUP="com.tuvium"
NEW_GROUP="io.github.markpollack"

# --- Guard: already renamed? ---
if [ -d "src/main/java/$NEW_DIR" ]; then
    echo "Already renamed — $NEW_DIR exists. Nothing to do."
    exit 0
fi

if [ ! -d "src/main/java/$OLD_DIR" ]; then
    echo "ERROR: Source directory src/main/java/$OLD_DIR not found."
    exit 1
fi

echo "=== Renaming $OLD_PKG → $NEW_PKG ==="

# --- 1. Move source directories ---
echo "[1/5] Moving source directories..."

for root in src/main/java src/test/java; do
    if [ -d "$root/$OLD_DIR" ]; then
        mkdir -p "$root/$NEW_DIR"
        # Move contents, not the directory itself
        cp -r "$root/$OLD_DIR"/* "$root/$NEW_DIR/"
        rm -rf "$root/$OLD_DIR"
        # Clean up empty parent dirs
        rmdir --ignore-fail-on-non-empty "$root/com/tuvium" 2>/dev/null || true
        rmdir --ignore-fail-on-non-empty "$root/com" 2>/dev/null || true
    fi
done

echo "  Done."

# --- 2. Update Java files ---
echo "[2/5] Updating Java source files..."

find src -name '*.java' -type f -exec sed -i \
    -e "s|$OLD_PKG|$NEW_PKG|g" \
    {} +

echo "  Updated $(find src -name '*.java' -type f | wc -l) Java files."

# --- 3. Update pom.xml ---
echo "[3/5] Updating pom.xml..."

sed -i \
    -e "s|<groupId>$OLD_GROUP</groupId>|<groupId>$NEW_GROUP</groupId>|" \
    -e "s|<mainClass>$OLD_PKG|<mainClass>$NEW_PKG|" \
    pom.xml

echo "  Done."

# --- 4. Update resource files ---
echo "[4/5] Updating resource and config files..."

# application.yml — update any package references
if [ -f src/main/resources/application.yml ]; then
    sed -i "s|$OLD_PKG|$NEW_PKG|g" src/main/resources/application.yml
fi

echo "  Done."

# --- 5. Update documentation ---
echo "[5/5] Updating documentation..."

for f in CLAUDE.md plans/ROADMAP.md plans/learnings/*.md plans/VISION.md plans/DESIGN.md; do
    if [ -f "$f" ]; then
        sed -i "s|$OLD_PKG|$NEW_PKG|g" "$f"
        sed -i "s|com/tuvium/prreview|io/github/markpollack/prreview|g" "$f"
    fi
done

echo "  Done."

# --- Verify ---
echo ""
echo "=== Verification ==="

remaining=$(grep -r "com\.tuvium" src/ pom.xml CLAUDE.md 2>/dev/null | grep -v "Binary" | wc -l)
if [ "$remaining" -eq 0 ]; then
    echo "SUCCESS: No remaining references to com.tuvium"
else
    echo "WARNING: $remaining remaining references to com.tuvium:"
    grep -rn "com\.tuvium" src/ pom.xml CLAUDE.md 2>/dev/null | grep -v "Binary" || true
fi

echo ""
echo "Next steps:"
echo "  ./mvnw spring-javaformat:apply"
echo "  ./mvnw verify"
echo "  git add -A && git commit -m 'refactor: rename package to io.github.markpollack.prreview'"
