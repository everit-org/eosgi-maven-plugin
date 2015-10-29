eosgi-maven-plugin
==================

A Maven plugin that runs Integration Tests in OSGi containers and collects OSGi Bundle dependencies to make them available in any IDE.

Maven site: http://everit.org/eosgi-maven-plugin

## Build

```
mvn clean install -Dga.ua=UA-XXXXXXXX-X -Dga.cd.mac.address.hash=cdZZ -Dga.cd.plugin.version=cdYY
```

 - **-Dga.ua=UA-XXXXXXXX-X**: the Google Analytics tracking identifier. If not defined, then the usage statistics will not be collected.
 - **-Dga.cd.mac.address.hash=cdZZ**: the custom dimension in Google Analytics to MAC Address hash.
 The ZZ is number of the custom dimension index.
 - **-Dga.cd.plugin.version=cdYY**: the custom dimension in Google Analytics to plugin version.
 The YY is number of the custom dimension index.

[![Analytics](https://ga-beacon.appspot.com/UA-15041869-4/everit-org/eosgi-maven-plugin)](https://github.com/igrigorik/ga-beacon)
