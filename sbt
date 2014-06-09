#!/bin/bash
java -Dfile.encoding=UTF8 \
    -Dsbt.ivy.home=`dirname $0`/ivy-sbt \
    -Xmx1536M \
    -Xss1M \
    -XX:+CMSClassUnloadingEnabled \
    -XX:MaxPermSize=512m \
	-jar $(dirname $0)/sbt-launch.jar "$@"
