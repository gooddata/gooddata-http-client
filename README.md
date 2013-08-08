gooddata-http-client
====================

Contains HTTP client with ability to handle GoodData authentication.

## Usage

### Authentication using login

    // create HTTP client with your settings
    HttpClient httpClient = ...

    // create login strategy, which will obtain SST via login
    SSTRetrievalStrategy sstStrategy = new LoginSSTRetrievalStrategy(new DefaultHttpClient(),
         new HttpHost("server.com", 123),"user@domain.com", "my secret");

    // wrap your HTTP client into GoodData HTTP client
    HttpClient client = new GoodDataHttpClient(httpClient, sstStrategy);

    // use GoodData HTTP client
    HttpGet getProject = new HttpGet("/gdc/projects");
    getProject.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
    HttpResponse getProjectResponse = client.execute(httpHost, getProject);

### Authentication using super-secure token (SST)

    // create HTTP client
    HttpClient httpClient = ...

    // create login strategy (you must somehow obtain SST)
    SSTRetrievalStrategy sstStrategy = new SimpleSSTRetrievalStrategy("my super-secure token");

    // wrap your HTTP client into GoodData HTTP client
    HttpClient client = new GoodDataHttpClient(httpClient, sstStrategy);

    // use GoodData HTTP client
    HttpGet getProject = new HttpGet("/gdc/projects");
    getProject.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
    HttpResponse getProjectResponse = client.execute(httpHost, getProject);
