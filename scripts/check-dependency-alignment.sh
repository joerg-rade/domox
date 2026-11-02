#!/usr/bin/env bash
#
# Dependency-alignment guardrail for DoMoX.
#
# Why this exists:
#   The root pom's <parent> is org.apache.causeway.app:causeway-app-starter-parent, whose own
#   parent is causeway-bom — and causeway-bom pins `spring-boot.version`.  Our pom OVERRIDES
#   that property (deliberately: we also pin a long list of spring-boot-* artifacts), so the
#   two must be kept in step BY HAND.  When they drift, the classpath mixes releases: the
#   artifacts we manage explicitly resolve to one version, while anything we do NOT list
#   (e.g. spring-boot-micrometer-metrics) comes from the parent at another.  That is silent at
#   build time and fatal at startup:
#
#     APPLICATION FAILED TO START
#     MetricsAutoConfiguration.defaultMeterObservationHandler
#       -> DefaultMeterObservationHandler.builder(MeterRegistry) does not exist
#     (Boot 4.2.0-RC1 on the classpath expecting micrometer 1.18.0-RC1, but 1.18.0-M1 present)
#
#   This has happened twice in this repo (the other was a JUnit 5-vs-6 mix, see
#   docs/scratch_pad/retro-boot4-upgrade-2026-10-14.md), so it gets a check.
#
# What it checks:
#   1. our spring-boot.version            == causeway-bom's spring-boot.version
#   2. our spring-framework/core/jdbc     == the Spring Boot BOM's spring-framework.version
#   3. our spring-data.version            == the version the Boot BOM's spring-data-bom manages
#   4. every spring-* artifact we manage is pinned to one of the approved properties —
#      a literal version, or a one-off property like the former ${spring-web.version}, is how
#      the skew grew from one artifact to three.
#
# Usage:
#   bash scripts/check-dependency-alignment.sh
#
#   Configurable via env vars (defaults match the local dev setup):
#     DOMOX_POM   path to the root pom     (default: pom.xml)
#     DOMOX_M2    local Maven repository   (default: ~/.m2/repository)
#
# Exits 0 (with a SKIP note) when the parent BOM is not in the local repository yet — that
# happens before the first Maven build; run `mvn -q validate` once to populate it.
set -uo pipefail

POM="${DOMOX_POM:-pom.xml}"
M2="${DOMOX_M2:-$HOME/.m2/repository}"

[ -f "$POM" ] || { echo "ERROR: $POM not found (run from the repo root)"; exit 2; }

# --- helpers ----------------------------------------------------------------

# Reads the first <name>value</name> from a pom (properties are unique per pom).
prop() {
  grep -oE "<$1>[^<]*" "$2" 2>/dev/null | head -1 | sed 's/.*>//'
}

# Newest matching pom in the local repository (versions sort with -V).
newest_pom() {
  ls -1 $1 2>/dev/null | sort -V | tail -1
}

problems=0
fail() { printf '  [FAIL] %s\n' "$1"; problems=$((problems + 1)); }
ok()   { printf '  [OK]   %s\n' "$1"; }

# --- 1. spring-boot.version vs causeway-bom ---------------------------------

CAUSEWAY_BOM_DIR="$M2/org/apache/causeway/causeway-bom"
BOM=$(newest_pom "$CAUSEWAY_BOM_DIR/*/causeway-bom-*.pom")
if [ -z "$BOM" ] || [ ! -f "$BOM" ]; then
  echo "SKIP: causeway-bom not in $CAUSEWAY_BOM_DIR (run 'mvn -q validate' once, then re-run)"
  exit 0
fi

ours=$(prop spring-boot.version "$POM")
theirs=$(prop spring-boot.version "$BOM")
if [ -z "$ours" ]; then
  fail "spring-boot.version is not set in $POM"
elif [ "$ours" = "$theirs" ]; then
  ok "spring-boot.version $ours matches $(basename "$BOM")"
else
  fail "spring-boot.version $ours != $theirs (pinned by $(basename "$BOM"))"
  printf '         -> align <spring-boot.version> in %s with the parent chain, then rebuild:\n' "$POM"
  printf '            the explicitly managed spring-boot-* artifacts follow the pom property,\n'
  printf '            anything else follows the parent - mixing the two breaks startup.\n'
fi

# --- 2 & 3. Spring Framework / Spring Data vs the Boot BOM -------------------

BOOT_BOM="$M2/org/springframework/boot/spring-boot-dependencies/$ours/spring-boot-dependencies-$ours.pom"
if [ ! -f "$BOOT_BOM" ]; then
  fail "Spring Boot BOM not found: $BOOT_BOM (run 'mvn -q validate' once, then re-run)"
else
  boot_framework=$(prop spring-framework.version "$BOOT_BOM")
  for p in spring-framework.version spring-core.version spring-jdbc.version; do
    v=$(prop "$p" "$POM")
    [ -z "$v" ] && { fail "$p is not set in $POM"; continue; }
    if [ "$v" = "$boot_framework" ]; then
      ok "$p $v matches the Boot $ours BOM"
    else
      fail "$p $v != $boot_framework (spring-framework.version of the Boot $ours BOM)"
    fi
  done

  # Spring Data lives in its own BOM, imported by the Boot BOM — two hops.
  sd_bom_version=$(prop spring-data-bom.version "$BOOT_BOM")
  SD_BOM=$(newest_pom "$M2/org/springframework/data/spring-data-bom/$sd_bom_version/spring-data-bom-*.pom")
  if [ -z "$sd_bom_version" ]; then
    fail "could not read spring-data-bom.version from $(basename "$BOOT_BOM")"
  elif [ -z "$SD_BOM" ] || [ ! -f "$SD_BOM" ]; then
    fail "spring-data-bom $sd_bom_version not in the local repository (run 'mvn -q validate')"
  else
    sd_managed=$(grep -A2 '<artifactId>spring-data-jpa</artifactId>' "$SD_BOM" \
      | grep -oE '<version>[^<]*' | head -1 | sed 's/.*>//')
    ours_sd=$(prop spring-data.version "$POM")
    if [ -z "$ours_sd" ]; then
      fail "spring-data.version is not set in $POM"
    elif [ "$ours_sd" = "$sd_managed" ]; then
      ok "spring-data.version $ours_sd matches spring-data-bom $sd_bom_version"
    else
      fail "spring-data.version $ours_sd != $sd_managed (managed by spring-data-bom $sd_bom_version)"
    fi
  fi
fi


# --- 4. every managed spring-* artifact uses an approved version property ----

allowed='^\$\{(spring-boot|spring-framework|spring-core|spring-jdbc|spring-data|spring-ai)\.version\}$'
bad=$(
  sed -n '/<dependencyManagement>/,/<\/dependencyManagement>/p' "$POM" \
  | awk '
      /<artifactId>spring-/ {
        art=$0; sub(/.*<artifactId>/, "", art); sub(/<\/artifactId>.*/, "", art)
        pend=5; next
      }
      pend>0 && /<version>/ {
        v=$0; sub(/.*<version>/, "", v); sub(/<\/version>.*/, "", v)
        print art "\t" v; pend=0; next
      }
      pend>0 { pend-- }
    ' \
  | while IFS=$'\t' read -r art v; do
      printf '%s\n' "$v" | grep -qE "$allowed" || printf '%s\t%s\n' "$art" "$v"
    done
)
if [ -z "$bad" ]; then
  ok "every managed spring-* artifact is pinned to an approved version property"
else
  fail "spring-* artifacts pinned to a literal version or a one-off property:"
  printf '%s\n' "$bad" | sed 's/^/           /'
  printf '         -> use ${spring-framework.version} / ${spring-data.version} / ${spring-boot.version}\n'
  printf '            so a version bump cannot leave one artifact behind.\n'
fi

# --- verdict ----------------------------------------------------------------

if [ $problems -gt 0 ]; then
  echo
  echo "DEPENDENCY ALIGNMENT FAILED ($problems problem(s)) against $(basename "$BOM")."
  exit 1
fi
echo "OK: pom versions are aligned with the Causeway parent chain and the Spring Boot BOM."

