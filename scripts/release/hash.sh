#!/bin/bash

set -ex
set -o pipefail

sha512sum $1 > $1.sha512