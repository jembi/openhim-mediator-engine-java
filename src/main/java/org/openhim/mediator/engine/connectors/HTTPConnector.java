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
import org.apache.http.Header;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.openhim.mediator.engine.CoreResponse;
import org.openhim.mediator.engine.MediatorRequestHandler;
import org.openhim.mediator.engine.messages.*;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
 * <li>MediatorHTTPRequest - responds with MediatorHTTPResponse</li>
 * <li>SetupHTTPSCertificate - no response</li>
 * </ul>
 */
public class HTTPConnector extends UntypedActor {

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private SSLContext sslContext;


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
        URIBuilder builder = new URIBuilder()
                .setScheme(req.getScheme())
                .setHost(req.getHost())
                .setPort(req.getPort())
                .setPath(req.getPath());
        if (req.getParams()!=null) {
            Iterator<String> iter = req.getParams().keySet().iterator();
            while (iter.hasNext()) {
                String param = iter.next();
                builder.addParameter(param, req.getParams().get(param));
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
        orchReq.setBody(req.getBody());
        orchReq.setPath(req.getPath());
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
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext);
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

                        //send response
                        MediatorHTTPResponse response = buildResponse(req, result);
                        req.getRespondTo().tell(response, getSelf());

                        //enrich engine response
                        CoreResponse.Orchestration orch = buildHTTPOrchestration(req, response);
                        req.getRequestHandler().tell(new AddOrchestrationToCoreResponse(orch), getSelf());
                    } catch (Exception ex) {
                        req.getRequestHandler().tell(new ExceptError(ex), getSelf());
                    } finally {
                        IOUtils.closeQuietly(result);
                    }
                }
            }, ec);
        } catch (URISyntaxException | UnsupportedEncodingException | UnsupportedOperationException ex) {
            req.getRequestHandler().tell(new ExceptError(ex), getSelf());
        }
    }


    private void setupHTTPSCertificate(SetupHTTPSCertificate msg) {
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream ksIn = new FileInputStream(new File(msg.getKeyStoreName()));
            ks.load(ksIn, msg.getKeyStorePassword().toCharArray());
            IOUtils.closeQuietly(ksIn);

            KeyStore ts = KeyStore.getInstance("JKS");
            FileInputStream tsIn = new FileInputStream(new File(msg.getTrustStoreName()));
            ts.load(tsIn, null);
            IOUtils.closeQuietly(tsIn);

            TrustStrategy strat = null;
            if (msg.getTrustSelfSigned()) {
                strat = new TrustSelfSignedStrategy();
            }

            sslContext = SSLContexts.custom()
                    .loadKeyMaterial(ks, msg.getKeyStorePassword().toCharArray())
                    .loadTrustMaterial(ts, strat)
                    .build();
        } catch (NoSuchAlgorithmException | KeyManagementException | UnrecoverableKeyException |
                KeyStoreException | IOException | CertificateException ex) {
            log.error(ex, "Exception during processing of SetupHTTPSCertificate message");
            sslContext = null;
        }
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof MediatorHTTPRequest) {
            sendRequest((MediatorHTTPRequest) msg);
        } else if (msg instanceof SetupHTTPSCertificate) {
            setupHTTPSCertificate((SetupHTTPSCertificate) msg);
        } else {
            unhandled(msg);
        }
    }
}
