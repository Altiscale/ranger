#!/bin/bash -l

# find this script and establish base directory
SCRIPT_DIR="$( dirname "${BASH_SOURCE[0]}" )"
echo "${BASH_SOURCE[0]}"
cd "$SCRIPT_DIR" &> /dev/null
MY_DIR="$(pwd)"
echo "[INFO] Executing in ${MY_DIR}"

# PATH does not contain ant in this login shell
export M2_HOME=/opt/mvn3
export JAVA_HOME=/opt/sapjvm_7
export FORREST_HOME=/opt/apache-forrest
export PATH=$M2_HOME/bin:$JAVA_HOME/bin:/opt/apache-ant/bin:$PATH


#------------------------------------------------------------------------------
#
#  ***** compile and package pig *****
#
#------------------------------------------------------------------------------

RANGER_VERSION="${RANGER_VERSION:-0.7.1}"
echo "Ranger version ${RANGER_VERSION}"
export ARTIFACT_VERSION="$RANGER_VERSION"

cd "$MY_DIR"
mvn versions:set -DnewVersion=${ARTIFACT_VERSION}
echo "Run UT = ${RUN_UNIT_TESTS}"
if [ "$RUN_UNIT_TESTS" == "true" ]; then
  mvn clean install assembly:assembly
else
  mvn -DskipTests -Drat.ignoreErrors=true clean install assembly:assembly
fi

cat /gen/src/target/rat.txt


#------------------------------------------------------------------------------
#
#  ***** setup the environment generating RPM via fpm *****
#
#------------------------------------------------------------------------------

