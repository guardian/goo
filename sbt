#!/bin/bash
java -Dfile.encoding=UTF8 -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=512m \
	-jar $(dirname $0)/sbt-launch.jar "$@"

