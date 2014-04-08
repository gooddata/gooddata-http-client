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
<dependency>
```

### <a name="credentials">Authentication Using Username And Password Credentials</a>

```Java
import com.gooddata.http.client.*
import java.io.IOException;
import org.apache.http.*;

~ ~ ~

// create a new client, pass user name and password credentials
HttpClient client = GoodDataHttpClient.withUsernamePassword(login, password).build();

// use HTTP client with transparent GoodData authentication
HttpGet getProject = new HttpGet("/gdc/projects");
getProject.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
HttpResponse getProjectResponse = client.execute(hostGoodData, getProject);

System.out.println(EntityUtils.toString(getProjectResponse.getEntity()));
```

### <a name="sst">Authentication Using Super-Secure Token (SST)</a>

If you already obtained SST by some other means and want the client to use that directly, you can do so by
creating the client as follows:

```Java
HttpClient client = GoodDataHttpClient.withSst(sst).build();
```

### <a name="tokenHost">Setting Token Host</a>

In certain cases you may want to obtain tokens from a different host than the one you are making your requests to
(e.g. you want to access GDC staging area - WebDav - which is running on a different host). In that case, you can
explicitly specify, which host will be used for retrieving tokens:

```Java
HttpHost hostGoodData = new HttpHost("secure.gooddata.com", 443, "https");

// create GDC http client explicitly specifying token host
HttpClient client = GoodDataHttpClient.withUsernamePassword(login, password)
                        .tokenHost(hostGoodData).build();
```

### <a name="wrappingHttpClient">Wrapping Existing Http Client</a>

There may be situations when you may want to wrap your existing HttpClient (e.g. if you want to set some HTTP headers
to be sent with every request). Here is how you can do so:

```Java
// create a HTTP client instance that will send X-GDC-Check-Domain set to false with every request
HttpClient internalClient = HttpClientBuilder.create().setDefaultHeaders(
        Arrays.asList(new BasicHeader("X-GDC-Check-Domain", "false"))
).build();

// wrap the http client in a GoodData HTTP client
HttpClient client = GoodDataHttpClient.withUsernamePassword(login, password)
                        .httpClient(internalClient).build();
```
