#!/bin/bash

# Pass through current working directory as system property
java -Dfile.encoding=UTF8 \
     -Dcwd="$CWD" \
     -Xmx512M \
     -Xss1M \
     -jar $(dirname $0)/sbt-launch.jar "$@"
