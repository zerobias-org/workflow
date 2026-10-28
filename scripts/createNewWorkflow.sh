#! /bin/sh
# Scaffold a new workflow package from templates/.
#   package/<vendor>/<product>            (depth 2)
#   package/<vendor>/<suite>/<product>    (depth 3)
# Portable: sed -i differs between GNU and BSD/macOS, so we use -i.bak + rm.

if [ $# -ne 1 ]; then
    echo "Usage: $0 <folder_path>"
    echo "  $0 package/acme/widget"
    echo "  $0 package/zerobias/zerobias/base"
    exit 1
fi

FOLDER_PATH=${1%/}
BASE_DIR=$(dirname "$0")

REL=${FOLDER_PATH#package/}
if [ "$REL" = "$FOLDER_PATH" ]; then
    echo "Error: path must start with package/"
    exit 1
fi

DEPTH=$(echo "$REL" | awk -F/ '{print NF}')
case "$DEPTH" in
    2) UP="../../../" ;;
    3) UP="../../../../" ;;
    *) echo "Error: use package/<vendor>/<product> or package/<vendor>/<suite>/<product>"; exit 1 ;;
esac

if [ -e "$FOLDER_PATH" ]; then
    echo "Error: $FOLDER_PATH already exists"
    exit 1
fi

mkdir -p "$FOLDER_PATH/activity"

# templates/. (not templates/*) so dotfiles like the REQUIRED .npmrc copy too
cp -r "$BASE_DIR"/../templates/. "$BASE_DIR/../$FOLDER_PATH"

PKG="$BASE_DIR/../$FOLDER_PATH/package.json"
sed -i.bak \
    -e "s|{npmparts}|$(echo "$REL" | tr / -)|g" \
    -e "s|{pkgparts}|$(echo "$REL" | tr / .)|g" \
    -e "s|{path}|$REL|g" \
    -e "s|{up}|$UP|g" \
    "$PKG"
rm -f "$PKG.bak"

echo "Scaffolded $FOLDER_PATH (package: $(echo "$REL" | tr / .).workflow)"
echo "Next: fill {description} in package.json, add activity/<code>.yml files"
echo "      (and workflow/, field/, role/<orgType>/ as needed — list each dir in"
echo "      package.json files), then: zbb --slot <slot> gate"
