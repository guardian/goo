#!/bin/bash
java -Dfile.encoding=UTF8 \
     -Xmx512M \
     -Xss1M \
     -jar $(dirname $0)/sbt-launch.jar "$@"
