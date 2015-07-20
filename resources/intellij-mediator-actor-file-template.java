#if (${PACKAGE_NAME} && ${PACKAGE_NAME} != "")package ${PACKAGE_NAME};#end

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.MediatorRequestMessage;
import org.openhim.mediator.engine.messages.SimpleMediatorRequest;
import org.openhim.mediator.engine.messages.SimpleMediatorResponse;

#parse("File Header.java")
public class ${NAME} extends UntypedActor {
    public static class ${NAME}Request extends SimpleMediatorRequest<Object> {
        public ${NAME}Request(ActorRef requestHandler, ActorRef respondTo, Object requestObject) {
            super(requestHandler, respondTo, requestObject);
        }
    }
 
    public static class ${NAME}Response extends SimpleMediatorResponse<Object> {
        public ${NAME}Response(MediatorRequestMessage originalRequest, Object responseObject) {
            super(originalRequest, responseObject);
        }
    }
    
    
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final MediatorConfig config;


    public ${NAME}(MediatorConfig config) {
        this.config = config;
    }


    @Override
    public void onReceive(Object msg) throws Exception {

        if (msg instanceof ${NAME}Request) {
            //TODO
            ${NAME}Response response = new ${NAME}Response((${NAME}Request)msg, "Not yet implemented!");
            ((${NAME}Request) msg).getRespondTo().tell(response, getSelf());
        } else {
            unhandled(msg);
        }
    }
}
