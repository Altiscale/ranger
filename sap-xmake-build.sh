#!/bin/bash -l

# find this script and establish base directory
SCRIPT_DIR="$( dirname "${BASH_SOURCE[0]}" )"
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
export ARTIFACT_VERSION="$RANGER_VERSION"

cd "$MY_DIR"
mvn versions:set -DnewVersion=${ARTIFACT_VERSION}
RUN_UNIT_TESTS="false"
if [ "$RUN_UNIT_TESTS" == "true" ]; then
  mvn -Drat.ignoreErrors=true clean install assembly:assembly
else
  mvn -DskipTests -X -Drat.ignoreErrors=true clean install assembly:assembly
fi

#------------------------------------------------------------------------------
#
#  ***** setup the environment generating RPM via fpm *****
#
#------------------------------------------------------------------------------
DATE_STRING=`date +%Y%m%d%H%M%S`
GIT_REPO="https://github.com/Altiscale/ranger"
ALTISCALE_RELEASE=${ALTISCALE_RELEASE:-0.1.0}

# convert the tarball into an RPM
#create the installation directory (to stage artifacts)
INSTALL_DIR="$MY_DIR/rangerrpmbuild"
echo "Install dir - ${INSTALL_DIR}"
mkdir -p --mode 0755 ${INSTALL_DIR}

OPT_DIR=${INSTALL_DIR}/opt/ranger-${ARTIFACT_VERSION}
echo "OPT_DIR - ${OPT_DIR}"
mkdir --mode=0755 -p ${OPT_DIR}
cd ${OPT_DIR}

WORKSPACE=$MY_DIR
ls ${WORKSPACE}/target/ranger-${ARTIFACT_VERSION}*.tar.gz | xargs -I{} tar -xvzpf {}
chmod 755 ${OPT_DIR}

ETC_DIR=${INSTALL_DIR}/etc/ranger-${ARTIFACT_VERSION}
mkdir --mode=0755 -p ${ETC_DIR}

# Add init.d scripts and sysconfig
mkdir --mode=0755 -p ${INSTALL_DIR}/etc/rc.d/init.d
cp ${WORKSPACE}/etc/init.d/* ${INSTALL_DIR}/etc/rc.d/init.d
mkdir --mode=0755 -p ${INSTALL_DIR}/etc/sysconfig
cp ${WORKSPACE}/etc/sysconfig/* ${INSTALL_DIR}/etc/sysconfig

# Add Ranger executables
mkdir --mode=0755 -p ${INSTALL_DIR}/usr/bin
cp -r ${INSTALL_DIR}/opt/ranger-${ARTIFACT_VERSION}/ranger-${ARTIFACT_VERSION}-admin ${INSTALL_DIR}/usr/bin/
cp -r ${INSTALL_DIR}/opt/ranger-${ARTIFACT_VERSION}/ranger-${ARTIFACT_VERSION}-usersync ${INSTALL_DIR}/usr/bin/

cd ${INSTALL_DIR}

# All config files:
export CONFIG_FILES="--config-files /etc/ranger-${ARTIFACT_VERSION} "

RPM_DESCRIPTION="Apache Ranger ${ARTIFACT_VERSION}\n\n${DESCRIPTION}"
export RPM_NAME=`echo alti-ranger-${ARTIFACT_VERSION}`
export RPM_DIR="${RPM_DIR:-"${INSTALL_DIR}/ranger-artifact/"}"
mkdir --mode=0755 -p ${RPM_DIR}
cd ${RPM_DIR}

fpm --verbose \
-s dir \
-t rpm \
-n ${RPM_NAME}  \
-v ${ALTISCALE_RELEASE} \
--maintainer support@altiscale.com \
--vendor Altiscale \
--provides ${RPM_NAME} \
--provides "libhdfs.so.0.0.0()(64bit)" \
--provides "libhdfs(x86-64)" \
--provides libhdfs \
--replaces alti-hadoop \
--depends 'lzo > 2.0' \
--url ${GIT_REPO} \
--license "Apache License v2" \
--iteration ${DATE_STRING} \
--description "$(printf "${RPM_DESCRIPTION}")" \
${CONFIG_FILES} \
--rpm-attr 755,root,root:/etc/rc.d/init.d/ranger_admin \
--rpm-user hadoop \
--rpm-group hadoop \
-C ${INSTALL_DIR} \
opt etc usr

find . -iname "*rpm"
#mv "${RPM_DIR}"/"${RPM_NAME}-${ALTISCALE_RELEASE}-${DATE_STRING}.x86_64.rpm" "${RPM_DIR}"/"alti-ranger-${ARTIFACT_VERSION}-SNAPSHOT.rpm"
mv "${RPM_DIR}"/"${RPM_NAME}"-"${ALTISCALE_RELEASE}"-"${DATE_STRING}".x86_64.rpm "${RPM_DIR}"/alti-tez-"${XMAKE_PROJECT_VERSION}".rpm

exit 0
