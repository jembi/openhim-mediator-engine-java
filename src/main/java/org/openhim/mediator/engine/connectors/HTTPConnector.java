/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.connectors;

import akka.actor.UntypedActor;
import akka.dispatch.OnComplete;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.openhim.mediator.engine.CoreResponse;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.MediatorRequestHandler;
import org.openhim.mediator.engine.messages.*;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import static akka.dispatch.Futures.future;

/**
 * An actor that provides functionality for connecting to HTTP services.
 * <br/><br/>
 * Supports the following messages:
 * <ul>
 * <li>{@link MediatorHTTPRequest} - responds with {@link MediatorHTTPResponse}</li>
 * <li>{@link SetupSSLContext} - response with {@link SetupSSLContextResponse}</li>
 * </ul>
 */
public class HTTPConnector extends UntypedActor {

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private SSLContext sslContext;
    private boolean sslTrustAll;


    private void copyHeaders(MediatorHTTPRequest src, HttpUriRequest dst) {
        if (src.getHeaders()!=null) {
            Iterator<String> iter = src.getHeaders().keySet().iterator();
            while (iter.hasNext()) {
                String header = iter.next();
                dst.addHeader(header, src.getHeaders().get(header));
            }
        }
    }

    private URI buildURI(MediatorHTTPRequest req) throws URISyntaxException {
        URIBuilder builder;

        if (req.getUri()!=null) {
            builder = new URIBuilder(req.getUri());
        } else {
            builder = new URIBuilder()
                    .setScheme(req.getScheme())
                    .setHost(req.getHost())
                    .setPort(req.getPort())
                    .setPath(req.getPath());
        }

        if (req.getParams()!=null) {
            for (Pair<String, String> param : req.getParams()) {
                builder.addParameter(param.getKey(), param.getValue());
            }
        }

        return builder.build();
    }

    private HttpUriRequest buildApacheHttpRequest(MediatorHTTPRequest req) throws URISyntaxException, UnsupportedEncodingException {
        HttpUriRequest uriReq;

        switch (req.getMethod()) {
            case "GET":
                uriReq = new HttpGet(buildURI(req));
                break;
            case "POST":
                uriReq = new HttpPost(buildURI(req));
                StringEntity entity = new StringEntity(req.getBody());
                ((HttpPost) uriReq).setEntity(entity);
                break;
            case "PUT":
                uriReq = new HttpPut(buildURI(req));
                StringEntity putEntity = new StringEntity(req.getBody());
                ((HttpPut) uriReq).setEntity(putEntity);
                break;
            case "DELETE":
                uriReq = new HttpDelete(buildURI(req));
                break;
            default:
                throw new UnsupportedOperationException(req.getMethod() + " requests not supported");
        }

        copyHeaders(req, uriReq);
        return uriReq;
    }


    private MediatorHTTPResponse buildResponseFromOpenHIMJSONContent(MediatorHTTPRequest req, CloseableHttpResponse apacheResponse) throws IOException, CoreResponse.ParseException {
        String content = IOUtils.toString(apacheResponse.getEntity().getContent());
        CoreResponse parsedContent = CoreResponse.parse(content);
        if (parsedContent.getResponse()==null) {
            throw new CoreResponse.ParseException(new Exception("No response object found in application/json+openhim content"));
        }

        int status = apacheResponse.getStatusLine().getStatusCode();
        if (parsedContent.getResponse().getStatus()!=null) {
            status = parsedContent.getResponse().getStatus();
        }

        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Header hdr : apacheResponse.getAllHeaders()) {
            headers.put(hdr.getName(), hdr.getValue());
        }

        if (parsedContent.getResponse().getHeaders()!=null) {
            for (String hdr : parsedContent.getResponse().getHeaders().keySet()) {
                headers.put(hdr, parsedContent.getResponse().getHeaders().get(hdr));
            }
        }

        if (parsedContent.getOrchestrations()!=null) {
            for (CoreResponse.Orchestration orch : parsedContent.getOrchestrations()) {
                req.getRequestHandler().tell(new AddOrchestrationToCoreResponse(orch), getSelf());
            }
        }

        if (parsedContent.getProperties()!=null) {
            for (String prop : parsedContent.getProperties().keySet()) {
                req.getRequestHandler().tell(new PutPropertyInCoreResponse(prop, parsedContent.getProperties().get(prop)), getSelf());
            }
        }

        return new MediatorHTTPResponse(req, parsedContent.getResponse().getBody(), status, headers);
    }

    private MediatorHTTPResponse buildResponseFromContent(MediatorHTTPRequest req, CloseableHttpResponse apacheResponse) throws IOException {
        String content = null;
        if (apacheResponse.getEntity()!=null && apacheResponse.getEntity().getContent()!=null) {
            content = IOUtils.toString(apacheResponse.getEntity().getContent());
        }

        int status = apacheResponse.getStatusLine().getStatusCode();

        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Header hdr : apacheResponse.getAllHeaders()) {
            headers.put(hdr.getName(), hdr.getValue());
        }

        return new MediatorHTTPResponse(req, content, status, headers);
    }

    private String getContentType(CloseableHttpResponse response) {
        if (response.getAllHeaders()==null) {
            return null;
        }

        for (Header hdr : response.getAllHeaders()) {
            if ("Content-Type".equalsIgnoreCase(hdr.getName())) {
                return hdr.getValue();
            }
        }

        return null;
    }

    private MediatorHTTPResponse buildResponse(MediatorHTTPRequest req, CloseableHttpResponse apacheResponse) throws IOException, CoreResponse.ParseException {
        String contentType = getContentType(apacheResponse);

        if (contentType!=null && contentType.contains(MediatorRequestHandler.OPENHIM_MIME_TYPE)) {
            return buildResponseFromOpenHIMJSONContent(req, apacheResponse);
        } else {
            return buildResponseFromContent(req, apacheResponse);
        }
    }

    private CoreResponse.Orchestration buildHTTPOrchestration(MediatorHTTPRequest req, MediatorHTTPResponse resp) {
        CoreResponse.Orchestration orch = new CoreResponse.Orchestration();
        orch.setName(req.getOrchestration());

        CoreResponse.Request orchReq = new CoreResponse.Request();
        if (req.getUri()==null) {
            orchReq.setHost(req.getHost());
            orchReq.setPort(Integer.toString(req.getPort()));
            orchReq.setPath(req.getPath());
        }
        orchReq.setBody(req.getBody());
        orchReq.setMethod(req.getMethod());
        orchReq.setHeaders(req.getHeaders());
        orch.setRequest(orchReq);

        CoreResponse.Response orchResp = new CoreResponse.Response();
        orchResp.setBody(resp.getBody());
        orchResp.setStatus(resp.getStatusCode());
        orchResp.setHeaders(resp.getHeaders());
        orch.setResponse(orchResp);

        return orch;
    }


    private CloseableHttpClient getHttpClient(final MediatorHTTPRequest req) {
        if (sslContext!=null && "https".equalsIgnoreCase(req.getScheme())) {
            SSLConnectionSocketFactory sslsf;

            if (sslTrustAll) {
                log.warning("SSL: Creating connection using 'trust all' option");
                sslsf = new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            } else {
                sslsf = new SSLConnectionSocketFactory(sslContext);
            }

            return HttpClients.custom().setSSLSocketFactory(sslsf).build();
        } else {
            return HttpClients.createDefault();
        }
    }

    private void sendRequest(final MediatorHTTPRequest req) {
        try {
            final CloseableHttpClient client = getHttpClient(req);
            final HttpUriRequest apacheRequest = buildApacheHttpRequest(req);


            final ExecutionContext ec = getContext().dispatcher();
            Future<CloseableHttpResponse> f = future(new Callable<CloseableHttpResponse>() {
                public CloseableHttpResponse call() throws IOException {
                    return client.execute(apacheRequest);
                }
            }, ec);
            f.onComplete(new OnComplete<CloseableHttpResponse>() {
                @Override
                public void onComplete(Throwable throwable, CloseableHttpResponse result) throws Throwable {
                    try {
                        if (throwable != null) {
                            throw throwable;
                        }
                        MediatorHTTPResponse response = buildResponse(req, result);

                        //enrich engine response
                        CoreResponse.Orchestration orch = buildHTTPOrchestration(req, response);
                        req.getRequestHandler().tell(new AddOrchestrationToCoreResponse(orch), getSelf());

                        //send response
                        req.getRespondTo().tell(response, getSelf());
                    } catch (Exception ex) {
                        req.getRequestHandler().tell(new ExceptError(req, ex), getSelf());
                    } finally {
                        IOUtils.closeQuietly(result);
                    }
                }
            }, ec);
        } catch (URISyntaxException | UnsupportedEncodingException | UnsupportedOperationException ex) {
            req.getRequestHandler().tell(new ExceptError(req, ex), getSelf());
        }
    }


    private KeyStore loadKeyStore(MediatorConfig.KeyStore inKS) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        KeyStore ks = KeyStore.getInstance("JKS");
        InputStream inputStream;

        if (inKS.getFilename() != null) {
            inputStream = new FileInputStream(new File(inKS.getFilename()));
        } else {
            inputStream = inKS.getInputStream();
        }

        try {
            if (inKS.getPassword() != null) {
                ks.load(inputStream, inKS.getPassword().toCharArray());
            } else {
                ks.load(inputStream, null);
            }
        } finally {
            IOUtils.closeQuietly(inputStream);
        }

        return ks;
    }

    private void setupSSLContext(SetupSSLContext msg) {
        try {
            SSLContextBuilder builder = SSLContexts.custom();

            sslTrustAll = msg.getRequestObject().getTrustAll();
            if (sslTrustAll) {
                log.warning("SSL: Trusting all certificates. This option should be considered unsecure and should not be enabled in production environments.");
                builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
            }

            if (msg.getRequestObject().getKeyStore() != null) {
                KeyStore ks = loadKeyStore(msg.getRequestObject().getKeyStore());
                if (msg.getRequestObject().getKeyStore().getPassword() != null) {
                    builder.loadKeyMaterial(ks, msg.getRequestObject().getKeyStore().getPassword().toCharArray());
                } else {
                    builder.loadKeyMaterial(ks, null);
                }
            }

            for (MediatorConfig.KeyStore ts : msg.getRequestObject().getTrustStores()) {
                KeyStore ks = loadKeyStore(ts);
                builder.loadTrustMaterial(ks);
            }

            sslContext = builder.build();

            msg.getRespondTo().tell(new SetupSSLContextResponse(msg), getSelf());
        } catch (NoSuchAlgorithmException | KeyManagementException | UnrecoverableKeyException |
                KeyStoreException | IOException | CertificateException ex) {
            sslContext = null;
            msg.getRespondTo().tell(new SetupSSLContextResponse(msg, ex), getSelf());
        }
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof MediatorHTTPRequest) {
            sendRequest((MediatorHTTPRequest) msg);
        } else if (msg instanceof SetupSSLContext) {
            setupSSLContext((SetupSSLContext) msg);
        } else {
            unhandled(msg);
        }
    }
}
