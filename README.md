gooddata-http-client
====================

GoodData HTTP client is an extension of [Jakarta HTTP Commons Client](http://hc.apache.org/httpcomponents-client-4.2.x/index.html).
This specialized client transparently handles [GoodData authentication](http://developer.gooddata.com/article/authentication-via-api)
so you can focus on writing logic on top of [GoodData API](https://developer.gooddata.com/api).

## Design

```com.gooddata.http.client.GoodDataHttpClient``` central class implements [org.apache.http.client.HttpClient interface](http://hc.apache.org/httpcomponents-client-4.2.x/httpclient/apidocs/org/apache/http/client/HttpClient.html)
It allows seamless switch for existing code base currently using ```org.apache.http.client.HttpClient```. Business logic encapsulating
access to [GoodData API](https://developer.gooddata.com/api) should use ```org.apache.http.client.HttpClient``` interface
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
