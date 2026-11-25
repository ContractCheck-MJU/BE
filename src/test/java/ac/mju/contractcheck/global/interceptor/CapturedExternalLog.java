package ac.mju.contractcheck.global.interceptor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/** 외부 호출 로거에 쌓인 줄을 테스트에서 읽는다. 닫으면 로거를 원래대로 돌린다. */
public final class CapturedExternalLog implements AutoCloseable {

    private final Logger logger = (Logger) LoggerFactory.getLogger(ExternalCallLog.LOGGER_NAME);
    private final Level previous = logger.getLevel();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public CapturedExternalLog() {
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    public String text() {
        return events().stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(previous);
    }
}
