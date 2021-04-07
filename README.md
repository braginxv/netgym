## The '__netgym__' network client for java apps

'netgym' is a compact high performance asynchronous network client for various 
socket-based (TCP and UDP) connections. Among TCP features it
supports secure socket connections (TLS), HTTP/1.1, including:

1. Rest methods GET, POST (with arbitrary content, url-encoded, form-data), PUT, DELETE, OPTIONS
1. Diverse HTTP/1.1 connections: Single/Closable (per request), Keep-alive and Pipelining.
1. HTTPS over TLS connections using the latest version of TLS available on running JVM and coordinating with the remote
   server.

The basic feature is that the only one instance of client used to serve all socket-based connections and built on
limited thread pool. Furthermore, different stages or operations of one connection can perform parallel on the same
thread pool. This pool so-called fork-join thread pool provides large number of parallel operations over the small
number of threads.

![Thread model](images/netgym_thread_model.png)

As it is shown above, the following operations are executing parallel allowing to increase a performance:

1. Response data decryption (using TLS)
1. Gzip/deflate uncompressing of a response content
1. Outgoing data encryption (TLS)

## Prerequisites
Runtime assemblies of "netgym" library doesn't depend on other packages and requires only JDK 1.7 or higher.
Assembling of this package depends on JUnit 4 and Mockito testing frameworks.

## Typical usage
### Asynchronous mode

Suppose we need to download images listed in `images` variable from remote server.
 These images can be downloaded in parallel to maximize performance and minimize receiving time,
and furthermore it to be downloaded asynchronously to prevent blocking userspace 
thread (such as UI thread).
```java
final HttpRequestBuilder requestBuilder = new HttpRequestBuilder()
   .baseUrl("https://server/path/to/images/")
   .addHeader("User-Agent", "netgym network client")
   .configureConnection(HttpRequestBuilder.ConnectionType.Single);
// or .configureConnection(HttpRequestBuilder.ConnectionType.Persistent);
// or .configureConnection(HttpRequestBuilder.ConnectionType.Pipelining);
// or .configurePipeliningConnection(TIME_DELAY_BETWEEN_REQUEST_SENDING);

for (String imageToBeDownloaded: images) {
   // here the client will send request as https://server/path/to/images/${imageToBeDownloaded}
   requestBuilder.asyncRawGET(imageToBeDownloaded, result -> {
       // This is asyncronous response in a library worker thread.
       // Do not block this thread for a long time to preventing 
       //   block other operations with the network client
       
       // This error type isn't related with a server response,
       // it corresponds to that something wrong has occurred in the network client itself
       result.left().apply(System.err::println); 
       
       // If the image has been fetched normally
       result.right().apply(response -> {
           if (response.getCode() == HttpsURLConnection.HTTP_OK) {
               // use image content here, e.g. save it to file using response.getContent()
           } else {
               // report about server response
           }
       });
   });
}
```

### Synchronous mode

Sometimes it is assumed that the server's response must be present directly, 
and the program cannot be resumed without it. In other words user's thread 
would be blocked until a response is received. 
In these cases you'd use synchronous adapters like this:

```java
final HttpRequestBuilder requestBuilder = new HttpRequestBuilder()
        .baseUrl("https://klike.net/uploads/posts/2018-11/")
        .addHeader("User-Agent", "netgym 0.5-snapshot")
        .configureConnection(HttpRequestBuilder.ConnectionType.Persistent);

for (String imageToBeDownloaded: images) {
    ResultedCompletion<Response> responseCompletion = requestBuilder.syncRawGET(resource);
    Either<String, Response> serverResponse = responseCompletion.awaitResult();

    serverResponse.left().apply(System.err::println);
    serverResponse.right().apply(response -> {
        // process response as it mentioned above
    });
}

// Other network operations with the client

ClientSystem.client().shutdown();
ClientSystem.client().awaitTerminating();
```

## HTTP connection types
The client supports following connections for HTTP/1.1

1. `httpRequestBuilder.configureConnection(HttpRequestBuilder.ConnectionType.Single)`
It's a closable TCP connection being terminated when server response has finished.
   Due to the fact that these connections are executed in parallel, and they do not depend on each other, 
   this type of connection can be fastest to perform parallel requests.
2. `httpRequestBuilder.configureConnection(HttpRequestBuilder.ConnectionType.Persistent)`
3. `httpRequestBuilder.configureConnection(HttpRequestBuilder.ConnectionType.Pipelining)`
4. `httpRequestBuilder.configurePipeliningConnection(TIME_DELAY_BETWEEN_REQUEST_SENDING)`
