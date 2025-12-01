# GoodData HTTP Client
[![Build Status](https://github.com/gooddata/gooddata-http-client/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/gooddata/gooddata-http-client/actions/workflows/build.yml) [![Javadocs](http://javadoc.io/badge/com.gooddata/gooddata-http-client.svg)](http://javadoc.io/doc/com.gooddata/gooddata-http-client) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.gooddata/gooddata-http-client/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.gooddata/gooddata-http-client) [![Release](https://img.shields.io/github/v/release/gooddata/gooddata-http-client.svg)](https://search.maven.org/artifact/com.gooddata/gooddata-http-client)

GoodData HTTP Client is an extension of [Apache HTTP Client 5](https://hc.apache.org/httpcomponents-client-5.x/index.html).
This specialized Java client transparently handles [GoodData authentication](https://help.gooddata.com/display/doc/API+Reference#/reference/authentication/log-in)
so you can focus on writing logic on top of [GoodData API](https://help.gooddata.com/display/doc/API+Reference).

**Version 3.0+ Migration Notice**: Starting from version 3.0.0, this library has been migrated from Apache HttpClient 4 to HttpClient 5.
This is a **breaking change** that requires code updates. See [Migration Guide](#migration) for details.

## Design

```com.gooddata.http.client.GoodDataHttpClient``` central class implements [org.apache.hc.client5.http.classic.HttpClient interface](https://hc.apache.org/httpcomponents-client-5.x/current/httpclient5/apidocs/org/apache/hc/client5/http/classic/HttpClient.html)
It allows seamless switch for existing code base currently using ```org.apache.hc.client5.http.classic.HttpClient```. Business logic encapsulating
access to [GoodData API](https://help.gooddata.com/display/doc/API+Reference) should use ```org.apache.hc.client5.http.classic.HttpClient``` interface
and keep ```com.gooddata.http.client.GoodDataHttpClient``` inside a factory class. ```com.gooddata.http.client.GoodDataHttpClient``` uses underlying ```org.apache.hc.client5.http.classic.HttpClient```.  A HTTP client
instance can be passed via the constructor.

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

```Java
import com.gooddata.http.client.*;
import java.io.IOException;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;

HttpHost hostGoodData = new HttpHost("https", "secure.gooddata.com", 443);

// create login strategy, which will obtain SST via credentials
SSTRetrievalStrategy sstStrategy = new LoginSSTRetrievalStrategy(login, password);

HttpClient client = new GoodDataHttpClient(HttpClientBuilder.create().build(), hostGoodData, sstStrategy);

// use HTTP client with transparent GoodData authentication
HttpGet getProject = new HttpGet("/gdc/projects");
getProject.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
ClassicHttpResponse getProjectResponse = client.execute(hostGoodData, getProject);

System.out.println(EntityUtils.toString(getProjectResponse.getEntity()));
```

### <a name="sst"/>Authentication using super-secure token (SST)</a>

```Java
import com.gooddata.http.client.*;
import java.io.IOException;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;

// create HTTP client
HttpClient httpClient = HttpClientBuilder.create().build();

HttpHost hostGoodData = new HttpHost("https", "secure.gooddata.com", 443);

// create login strategy (you must somehow obtain SST)
SSTRetrievalStrategy sstStrategy = new SimpleSSTRetrievalStrategy("my super-secure token");

// wrap your HTTP client into GoodData HTTP client
HttpClient client = new GoodDataHttpClient(httpClient, hostGoodData, sstStrategy);

// use GoodData HTTP client
HttpGet getProject = new HttpGet("/gdc/projects");
getProject.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
ClassicHttpResponse getProjectResponse = client.execute(hostGoodData, getProject);

System.out.println(EntityUtils.toString(getProjectResponse.getEntity()));
```

## <a name="migration"/>Migration from HttpClient 4 to HttpClient 5

**Version 3.0.0** introduces breaking changes due to the migration from Apache HttpClient 4 to HttpClient 5.

### Key Changes

#### Package Structure
- **HttpClient 4**: `org.apache.http.*`
- **HttpClient 5**: `org.apache.hc.client5.http.*` and `org.apache.hc.core5.http.*`

#### Interface Changes
- `HttpClient` → `org.apache.hc.client5.http.classic.HttpClient`
- `HttpResponse` → `org.apache.hc.core5.http.ClassicHttpResponse`
- `HttpRequest` → `org.apache.hc.core5.http.ClassicHttpRequest`

#### HttpHost Constructor
```java
// HttpClient 4
HttpHost host = new HttpHost("hostname", 443, "https");

// HttpClient 5
HttpHost host = new HttpHost("https", "hostname", 443);
```

#### Entity Creation
```java
// HttpClient 4
BasicHttpEntity entity = new BasicHttpEntity();
entity.setContent(new ByteArrayInputStream(content));

// HttpClient 5
StringEntity entity = new StringEntity(content, ContentType.TEXT_PLAIN);
```

#### Response Status
```java
// HttpClient 4
int status = response.getStatusLine().getStatusCode();

// HttpClient 5
int status = response.getCode();
```

### Required Dependencies

Update your `pom.xml`:

```xml
<dependency>
  <groupId>org.apache.httpcomponents.client5</groupId>
  <artifactId>httpclient5</artifactId>
  <version>5.5.1</version>
</dependency>
<dependency>
  <groupId>org.apache.httpcomponents.core5</groupId>
  <artifactId>httpcore5</artifactId>
  <version>5.3.6</version>
</dependency>
```

### Compatibility

- **Minimum Java Version**: Java 11+
- **HttpClient 4 compatibility**: Not maintained. Use version 1.x for HttpClient 4 compatibility.

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
