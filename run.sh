#!/bin/bash
export PATH="/usr/local/opt/openjdk@17/bin:$PATH"
cd "$(dirname "$0")"
javac -d out src/*.java && java -cp out LexerUI
