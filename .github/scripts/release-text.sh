#!/usr/bin/env bash
# Erzeugt die Beschreibung eines GitHub-Releases:
# Kernsatz, Abschnitt der Version aus CHANGELOG.md, Hinweis zur Installation und Angaben zur Echtheit.
# Aufruf: release-text.sh <versionName> <apk-datei>   (die APK darf fehlen, dann ohne Echtheitsangaben)
set -euo pipefail

VERSION="$1"
APK="$2"
KERNSATZ="Beamer-Fotos holt Fotos von jedem Handy per QR-Code auf den Beamer und zeigt sie als Diashow – ohne App auf dem Handy, ohne Cloud."

# Abschnitt dieser Version aus CHANGELOG.md herausschneiden (bis zur nächsten Überschrift „## [“).
ABSCHNITT=$(awk -v v="$VERSION" '
  /^## \[/ { if (found) exit; if (index($0, "## [" v "]") == 1) { found = 1; next } }
  found { print }
' CHANGELOG.md | sed '/./,$!d')
if [ -z "${ABSCHNITT//[[:space:]]/}" ]; then ABSCHNITT="Version $VERSION"; fi

echo "_${KERNSATZ}_"
echo
echo "$ABSCHNITT"
echo
echo "### Installation auf dem Beamer"
echo
echo "Auf dem Beamer (Google TV) die App „Downloader“ aus dem Play Store öffnen und diese Adresse eingeben:"
echo "\`https://github.com/${GITHUB_REPOSITORY:-boerni328-bene/Beamer-Fotos}/releases/latest/download/beamer-fotos.apk\`"
echo "Ausführliche Schritte stehen in der README. Ein Update wird einfach über die vorhandene App installiert, die Fotos bleiben erhalten."

if [ -f "$APK" ]; then
  PRUEFSUMME=$(sha256sum "$APK" | cut -d' ' -f1)
  APKSIGNER="$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -n 1)apksigner"
  FINGERABDRUCK=$("$APKSIGNER" verify --print-certs "$APK" 2>&1 \
    | grep -oiE 'certificate SHA-256 digest: *[0-9a-f]{64}' | grep -oiE '[0-9a-f]{64}' | head -n 1 \
    | tr 'a-f' 'A-F' | sed 's/../&:/g; s/:$//')
  echo
  echo "### Echtheit prüfen"
  echo
  echo "- Prüfsumme (SHA-256) der APK: \`$PRUEFSUMME\`"
  echo "- Fingerabdruck des Signaturzertifikats (SHA-256): \`$FINGERABDRUCK\`"
fi
