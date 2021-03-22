#!/usr/bin/env bash
set -euxo pipefail

apt-get -q=2 update

apt-get install -q=2 sqlite3

# Needed when activating PG Support on WD
apt-get install -q=2 libpq-dev

# Dependencies of libcore
apt-get install -q=2 libxext6 libxrender1 libxtst6

# Cleanup
apt-get clean
rm -rf -- /var/lib/apt/lists/*

exit 0
