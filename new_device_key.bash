#!/usr/bin/env bash
# Make a new device key for a camera or script, and the line that goes with it in the server's access.json.
#
#   ./new_device_key.bash <device-id>
#
# Prints the key ONCE: put it on the device (cameras: the file named by upload.key_file in their settings,
# CMO_KEY=<key>, mode 600) and nowhere else. The server keeps only the hash: add it to that device's
# "key_hashes" in access.json (a device may have two, so a key can be changed without downtime). The server
# reads access.json again by itself when it changes.
set -euo pipefail
device=${1:?usage: $0 <device-id>}
key="cmo_$(openssl rand -hex 32)"
hash="sha256:$(printf '%s' "$key" | sha256sum | cut -d' ' -f1)"
echo "device:  $device"
echo "key:     $key"
echo "hash:    $hash"
echo
echo "On the device (e.g. ~/.config/cmo/device_key, chmod 600):"
echo "  CMO_KEY=$key"
echo
echo "In access.json, the device's entry:"
echo "  {\"id\": \"$device\", \"account\": \"<account>\", \"role\": \"camera\", \"cameras\": [\"<camera>\"], \"key_hashes\": [\"$hash\"]}"
