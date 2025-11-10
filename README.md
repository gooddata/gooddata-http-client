# GoodData HTTP Client
[![Build Status](https://github.com/gooddata/gooddata-http-client/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/gooddata/gooddata-http-client/actions/workflows/build.yml) [![Javadocs](http://javadoc.io/badge/com.gooddata/gooddata-http-client.svg)](http://javadoc.io/doc/com.gooddata/gooddata-http-client) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.gooddata/gooddata-http-client/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.gooddata/gooddata-http-client) [![Release](https://img.shields.io/github/v/release/gooddata/gooddata-http-client.svg)](https://search.maven.org/artifact/com.gooddata/gooddata-http-client)

GoodData HTTP Client is an extension of [Apache HTTP Client 5.x](https://hc.apache.org/httpcomponents-client-5.3.x/index.html).
This specialized Java client transparently handles [GoodData authentication](https://help.gooddata.com/display/doc/API+Reference#/reference/authentication/log-in)
so you can focus on writing logic on top of [GoodData API](https://help.gooddata.com/display/doc/API+Reference).

## ⚠️ Version 2.0+ Breaking Changes

**Version 2.0.0** introduces a major update migrating from Apache HttpClient 4.x to 5.x. See the [Migration Guide](#migration-guide) below for upgrade instructions.

## Requirements

- **Java 17+** (updated from Java 8)
- **Apache HttpClient 5.5+** (migrated from 4.x)
- **Maven 3.6+** (for building)

## Design

`com.gooddata.http.client.GoodDataHttpClient` is a thread-safe HTTP client that wraps Apache HttpClient 5.x and provides transparent GoodData authentication handling. The client automatically manages SST (Super Secure Token) and TT (Temporary Token) lifecycle, including:

- Automatic token refresh on expiration
- Retry logic for authentication failures  
- Thread-safe token management
- Support for all HTTP methods (GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS)

Business logic should use the `GoodDataHttpClient` class directly, which handles all authentication concerns internally.

## Usage

Authentication to GoodData is supported by [credentials](#credentials) or [Super Secure Token](#sst).

If your project is managed by Maven you can add GoodData HTTP client as a new dependency otherwise you have to
[download binary](#http://search.maven.org/#browse%7C458832843) and add manually.

*Maven*

```XML
<dependency>
  <groupId>com.gooddata</groupId>
  <artifactId>gooddata-http-client</artifactId>
  <version>${gdc.http.client.version}</version>
</dependency>
```

### <a name="credentials"/>Authentication using credentials</a>

```java
import com.gooddata.http.client.*;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;

HttpHost hostGoodData = new HttpHost("https", "secure.gooddata.com", 443);

// Create login strategy, which will obtain SST via credentials
SSTRetrievalStrategy sstStrategy = new LoginSSTRetrievalStrategy(login, password);

// Create GoodData HTTP client
GoodDataHttpClient client = new GoodDataHttpClient(
    HttpClients.createDefault(), 
    hostGoodData, 
    sstStrategy
);

// Use HTTP client with transparent GoodData authentication
HttpGet getProject = new HttpGet("/gdc/projects");
getProject.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
ClassicHttpResponse getProjectResponse = client.execute(hostGoodData, getProject);

System.out.println(EntityUtils.toString(getProjectResponse.getEntity()));
```

### <a name="sst"/>Authentication using super-secure token (SST)</a>

```java
import com.gooddata.http.client.*;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;

HttpHost hostGoodData = new HttpHost("https", "secure.gooddata.com", 443);

// Create login strategy (you must somehow obtain SST)
SSTRetrievalStrategy sstStrategy = new SimpleSSTRetrievalStrategy("my super-secure token");

// Create GoodData HTTP client
GoodDataHttpClient client = new GoodDataHttpClient(
    HttpClients.createDefault(), 
    hostGoodData, 
    sstStrategy
);

// Use GoodData HTTP client
HttpGet getProject = new HttpGet("/gdc/projects");
getProject.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
ClassicHttpResponse getProjectResponse = client.execute(hostGoodData, getProject);

System.out.println(EntityUtils.toString(getProjectResponse.getEntity()));
```

## Migration Guide

### Migrating from 1.x to 2.0+ (Apache HttpClient 4.x to 5.x)

Version 2.0.0 introduces breaking changes due to the Apache HttpClient 5.x migration. Follow these steps to upgrade:

#### 1. Update Dependencies

**Maven:**
```xml
<dependency>
  <groupId>com.gooddata</groupId>
  <artifactId>gooddata-http-client</artifactId>
  <version>2.0.0</version> <!-- Updated from 1.x -->
</dependency>
```

#### 2. Update Java Version

Ensure your project uses **Java 17 or higher**:
```xml
<maven.compiler.source>17</maven.compiler.source>
<maven.compiler.target>17</maven.compiler.target>
```

#### 3. Update Imports

Replace Apache HttpClient 4.x imports with 5.x equivalents:

**Before (1.x):**
```java
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
```

**After (2.0+):**
```java
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
```

#### 4. Update HttpHost Construction

**Before (1.x):**
```java
HttpHost host = new HttpHost("secure.gooddata.com", 443, "https");
```

**After (2.0+):**
```java
HttpHost host = new HttpHost("https", "secure.gooddata.com", 443);
// Note: scheme is now the first parameter
```

#### 5. Update Response Handling

**Before (1.x):**
```java
HttpResponse response = client.execute(host, request);
```

**After (2.0+):**
```java
ClassicHttpResponse response = client.execute(host, request);
```

#### 6. Update HttpClient Creation

**Before (1.x):**
```java
HttpClient httpClient = HttpClientBuilder.create().build();
```

**After (2.0+):**
```java
HttpClient httpClient = HttpClients.createDefault();
// Or with custom configuration:
HttpClient httpClient = HttpClients.custom()
    .setDefaultRequestConfig(RequestConfig.custom()
        .setConnectionRequestTimeout(Timeout.ofSeconds(30))
        .build())
    .build();
```

#### 7. Key Behavioral Changes

- **Thread Safety**: All requests now use write locks for consistency. This may reduce throughput under high concurrency but ensures reliable token management.
- **Entity Handling**: Non-repeatable request entities are automatically buffered for retry scenarios.
- **Error Handling**: More specific exceptions for authentication failures.
- **HTTP Methods**: Full support for POST, PUT, PATCH in addition to GET and DELETE.

#### 8. Testing Your Migration

After updating your code:

1. **Compile**: Ensure no compilation errors
2. **Test**: Run your existing test suite
3. **Integration Test**: Test against GoodData API with real credentials
4. **Monitor**: Watch for authentication issues or performance changes

#### Common Migration Issues

**Issue: NoClassDefFoundError**
- **Cause**: Conflicting HttpClient versions in classpath
- **Fix**: Use `mvn dependency:tree` to identify conflicts and exclude old HttpClient 4.x dependencies

**Issue: Method not found errors**
- **Cause**: Using old HttpClient 4.x APIs
- **Fix**: Update all imports and method calls to HttpClient 5.x equivalents

**Issue: Authentication failures**
- **Cause**: Token handling differences
- **Fix**: Ensure SST/TT tokens are being passed correctly; check logs for authentication errors

### Need Help?

- Review the [complete API documentation](http://javadoc.io/doc/com.gooddata/gooddata-http-client)
- Check [Apache HttpClient 5.x migration guide](https://hc.apache.org/httpcomponents-client-5.3.x/migration-guide/index.html)
- Report issues on [GitHub](https://github.com/gooddata/gooddata-http-client/issues)

## Build

```
mvn package
```

### Unit tests
```
mvn test
```

### Acceptance tests (with real backend)

```
mvn -P at clean verify -DGDC_LOGIN=user@email.com -DGDC_PASSWORD=password [-DGDC_BACKEND=<backend host>]
```

### Test coverage
One can check test coverage report in [coveralls.io](https://coveralls.io/github/gooddata/gooddata-http-client).

## Notice

GoodData Corporation provides this software "as-is" under conditions
specified in the [license](LICENSE.txt).
