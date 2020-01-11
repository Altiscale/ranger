artifacts builderVersion: "1.1", {

  group "com.sap.bds.ats-altiscale", {

    artifact "ranger", {
      file "${gendir}/src/rangerrpmbuild/ranger-artifact/alti-ranger-${buildVersion}.rpm"
    }
  }
}
