# GoodData HTTP Client
[![Build Status](https://github.com/gooddata/gooddata-http-client/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/gooddata/gooddata-http-client/actions/workflows/build.yml) [![Javadocs](http://javadoc.io/badge/com.gooddata/gooddata-http-client.svg)](http://javadoc.io/doc/com.gooddata/gooddata-http-client) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.gooddata/gooddata-http-client/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.gooddata/gooddata-http-client) [![Release](https://img.shields.io/github/v/release/gooddata/gooddata-http-client.svg)](https://search.maven.org/artifact/com.gooddata/gooddata-http-client)

GoodData HTTP Client is an extension of [Apache HTTP Client](http://hc.apache.org/httpcomponents-client-4.3.x/index.html) (former Jakarta Commons).
This specialized Java client transparently handles [GoodData authentication](https://help.gooddata.com/display/doc/API+Reference#/reference/authentication/log-in)
so you can focus on writing logic on top of [GoodData API](https://help.gooddata.com/display/doc/API+Reference).

## Design

```com.gooddata.http.client.GoodDataHttpClient``` central class implements [org.apache.http.client.HttpClient interface](http://hc.apache.org/httpcomponents-client-4.2.x/httpclient/apidocs/org/apache/http/client/HttpClient.html)
It allows seamless switch for existing code base currently using ```org.apache.http.client.HttpClient```. Business logic encapsulating
access to [GoodData API](https://help.gooddata.com/display/doc/API+Reference) should use ```org.apache.http.client.HttpClient``` interface
and keep ```com.gooddata.http.client.GoodDataHttpClient``` inside a factory class. ```com.gooddata.http.client.GoodDataHttpClient``` uses underlying ```org.apache.http.client.HttpClient```.  A HTTP client
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
import com.gooddata.http.client.*
import java.io.IOException;
import org.apache.http.*;

HttpHost hostGoodData = new HttpHost("secure.gooddata.com", 443, "https");

// create login strategy, which will obtain SST via credentials
SSTRetrievalStrategy sstStrategy = new LoginSSTRetrievalStrategy(login, password);

HttpClient client = new GoodDataHttpClient(HttpClientBuilder.create().build(), hostGoodData, sstStrategy);

// use HTTP client with transparent GoodData authentication
HttpGet getProject = new HttpGet("/gdc/projects");
getProject.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
HttpResponse getProjectResponse = client.execute(hostGoodData, getProject);

System.out.println(EntityUtils.toString(getProjectResponse.getEntity()));
```

### <a name="sst"/>Authentication using super-secure token (SST)</a>

```Java
import com.gooddata.http.client.*
import java.io.IOException;
import org.apache.http.*;

// create HTTP client
HttpClient httpClient = HttpClientBuilder.create().build();

HttpHost hostGoodData = new HttpHost("secure.gooddata.com", 443, "https");

// create login strategy (you must somehow obtain SST)
SSTRetrievalStrategy sstStrategy = new SimpleSSTRetrievalStrategy("my super-secure token");

// wrap your HTTP client into GoodData HTTP client
HttpClient client = new GoodDataHttpClient(httpClient, hostGoodData, sstStrategy);

// use GoodData HTTP client
HttpGet getProject = new HttpGet("/gdc/projects");
getProject.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
HttpResponse getProjectResponse = client.execute(hostGoodData, getProject);

System.out.println(EntityUtils.toString(getProjectResponse.getEntity()));
```

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
