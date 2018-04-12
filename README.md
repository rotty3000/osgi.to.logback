## OSGi Log 1.4 to Logback

With **OSGi R7** the **Log Service Specification** brings a slew of new features designed to improve the developer experience with logging, the details of which can be read about [here](https://osgi.org/specification/osgi.cmpn/7.0.0/service.log.html).

This project is intended to help simplify the logging story for OSGi by leveraging many capabilities of [Logback](https://logback.qos.ch/) together with the new Log 1.4 features and providing a **unified backend** where:

1. all logging is configured in one place
2. all log records are appended together (or at least all appenders are setup in one place)

### Depedencies

Add Logback to an OSGi framework by adding the following bundles:

```xml
<dependency>
	<groupId>ch.qos.logback</groupId>
	<artifactId>logback-classic</artifactId>
	<version>1.2.x</version>
</dependency>
<dependency>
	<groupId>ch.qos.logback</groupId>
	<artifactId>logback-core</artifactId>
	<version>1.2.x</version>
</dependency>
<dependency>
	<groupId>org.slf4j</groupId>
	<artifactId>slf4j-api</artifactId>
	<version>1.7.x</version>
</dependency>
```

This provides `slf4j` logging API over the Logback backend.

### Configuration

Configuring logback is most easily handled by setting the system property `logback.configurationFile` to point to a file on the file system.

An example using a bndrun file looks like 

`-runproperties: logback.configurationFile=file:${.}/logback.xml`

where `${.}` gives the path to the directory of the bndrun file.

Logback offers many features from it's configuration file so make sure to look through the documentation.

### Unified Backend

Of course adding Logback does not magically result in all logs funnelling into the same appenders, particularly the OSGi logs. To begin you need an implementation of the OSGi Log 1.4 specification (currently only equinox supports as it is the Log Specification RI):

```xml
<!-- currently only available as source or ibuild -->
<dependency>
    <groupId>org.eclipse.platform</groupId>
    <artifactId>org.eclipse.osgi</artifactId>
    <version>3.13.x</version>
</dependency>
```

Next install **osgi-to-logback** which sends all OSGi Log events directly to Logback and helps integrate Logback configuration via [`LoggerAdmin`](https://osgi.org/specification/osgi.cmpn/7.0.0/service.log.html#d0e2442): 

```xml
<!-- TODO release osgi-to-log -->
```

#### Additional Logging APIs

There are probably several other logging frameworks already used by the bundles in the framework. Several of these already map their events onto the slf4j API.

- **log4j 1.2.x** - deploy the dependency

  ```xml
  <dependency>
  	<groupId>org.slf4j</groupId>
  	<artifactId>log4j-over-slf4j</artifactId>
  	<version>1.7.25</version>
  </dependency>
  ```

- **log4j 2.x** - deploy the dependency

  ```xml
  <dependency>
  	<groupId>org.apache.logging.log4j</groupId>
  	<artifactId>log4j-api</artifactId>
  	<version>2.x</version>
  </dependency>
  ```

- **commons-logging 1.1.x** - deploy the dependency

  ```xml
  <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-jcl</artifactId>
      <version>1.7.25</version>
  </dependency>
  ```

- **jboss-logging 3.2.x** - deploy the dependency

  ``` xml
  <dependency>
      <groupId>org.jboss.logging</groupId>
      <artifactId>jboss-logging</artifactId>
      <version>3.2.x</version>
  </dependency>
  ```
  Set the system property `org.jboss.logging.provider=slf4j`

- **JUL** - configure Logback to handle JUL logging by using the following configuration:

  ```xml
  <contextListener class="ch.qos.logback.classic.jul.LevelChangePropagator">
  	<resetJUL>true</resetJUL>
  </contextListener>
  ```


### Mapping of OSGi Events


The OSGi Log specification describes events resulting in log records. Log 1.4 defines logger name mapping to these events.

| Event           | Logger Name        | Events types                                                 |
| --------------- | ------------------ | ------------------------------------------------------------ |
| Bundle event    | `Events.Bundle`    | `INSTALLED` - BundleEvent INSTALLED<br />`STARTED` - BundleEvent STARTED<br />`STOPPED` - BundleEvent STOPPED<br />`UPDATED` - BundleEvent UPDATED<br />`UNINSTALLED` - BundleEvent UNINSTALLED<br />`RESOLVED` - BundleEvent RESOLVED<br />`UNRESOLVED` - BundleEvent UNRESOLVED |
| Service event   | `Events.Service`   | `REGISTERED` - ServiceEvent REGISTERED<br /> `MODIFIED` - ServiceEvent MODIFIED<br /> `UNREGISTERING` - ServiceEvent UNREGISTERING |
| Framework event | `Events.Framework` | `STARTED` - FrameworkEvent STARTED<br />`ERROR` - FrameworkEvent ERROR<br />`PACKAGES_REFRESHED` - FrameworkEvent PACKAGES REFRESHED<br />`STARTLEVEL_CHANGED` - FrameworkEvent STARTLEVEL CHANGED<br />`WARNING` - FrameworkEvent WARNING<br />`INFO` - FrameworkEvent INFO |

In order to control the granularity of the logging associated with these events, **osgi-to-logback** appends a period (`.`) and the `Bundle-SymbolicName` of the originating bundle. 

Consider the following `logback.xml` example:

```xml
<configuration>
    
	<!-- reset JUL log levels and set Logback as the backend -->
	<contextListener class="ch.qos.logback.classic.jul.LevelChangePropagator">
		<resetJUL>true</resetJUL>
	</contextListener>

    <!-- define appenders, here is a simple console appender -->
	<appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
		<encoder>
			<pattern>%d{HH:mm:ss.SSS} [%.15thread] %-5level %logger{36}:%line - %msg%n</pattern>
		</encoder>
	</appender>

    <!-- globaly set bundle & framework events to WARN -->
	<logger name="Events.Bundle" level="WARN"/>
	<logger name="Events.Framework" level="WARN"/>
    
    <!-- globally set service events to INFO -->
	<logger name="Events.Service" level="INFO"/>
    
    <!-- exclude service events from the equinox framework, just because -->
	<logger name="Events.Service.org.eclipse.osgi" level="WARN"/>
    
    <!-- ignore one particularly bundle using the old LogService API -->
	<logger name="LogService.org.baz" level="OFF"/>

    <!-- the rest are typical logger names associated with the uses of the various 
         logger API calls -->
	<logger name="org.my.foo" level="DEBUG"/>
	<logger name="org.eclipse" level="ERROR"/>
	<logger name="org.jboss" level="ERROR"/>

	<root level="ERROR">
		<appender-ref ref="STDOUT" />
	</root>
</configuration>
```

### Example BNDRUNs

The following are complete examples of [`bndrun`](http://bnd.bndtools.org/chapters/300-launching.html) files.

#### Standard

The **standard** approach is to install the bundles into the OSGi runtime:

```properties
-runrequires: \
	osgi.identity;filter:='(osgi.identity=osgi.to.logback)'

## Requiring only `osgi.to.logback` _should_ resolve these other bundles
-runbundles: \
	ch.qos.logback.classic;version='[1.2.3,1.2.4)',\
	ch.qos.logback.core;version='[1.2.3,1.2.4)',\
	slf4j.api;version='[1.7.25,1.7.26)',\
	osgi.to.logback;version='[0.1.0,0.1.1)',\
	...

-runproperties: \
	logback.configurationFile=file:${.}/logback.xml
```

_**Note**_ that the `-runbundles` directive is ordered. Therefore it's probably reasonable to place these bundles at the head of the list if only to start capturing logs as early as possible.

#### LOG EVERYTHING

The **log everything** approach is intended to integrate with the framework as early as possible to capture all activities without resorting to the log cache or being concerned with missed logs due to start ordering (this is only tested currently on Eclipse equinox):

```properties
-runpath: \
	ch.qos.logback.classic;version='[1.2.3,1.2.4)',\
	ch.qos.logback.core;version='[1.2.3,1.2.4)',\
	slf4j.api;version='[1.7.25,1.7.26)',\
	osgi.to.logback;version='[0.1.0,0.1.1)'
-runsystempackages: \
	org.slf4j;version=1.7.25,\
	org.slf4j.helpers;version=1.7.25,\
	org.slf4j.spi;version=1.7.25

-runproperties: \
	logback.configurationFile=file:${.}/logback.xml
```



_**Note**_ that there are no `-runrequires` for the dependent bundles in this mode.

_**Note**_ that for or a production runtime, you can specify the system property `-Dlogback.configurationFile=file:../logback.xml` (which overrides what is captured in the bndrun/executable) to point to a config file somewhere in the production system.

### Asserted Notes

- **osgi-to-logback** supports Logback's [automatic reloading](https://logback.qos.ch/manual/configuration.html#autoScan) upon file modification
- When using **equinox** framework one may want to disable it's own internal appenders using the system property `eclipse.log.enabled=false`
- A caveat of `Events.Bundle`, `Events.Framework` & `Events.Service` is that they must first be set to an effecrive level before the sub loggers will output records. It's because the integration is not deeply integration for these logger names. A change would have to occur at the framework level to make that possible.

