package cn.lineai.feature.chat;

import org.junit.Test;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;

import static org.junit.Assert.assertEquals;

public class ChatFailureTest {

    @Test
    public void rawDetailStaysUnchangedForTheTranscriptRow() {
        DomainException error = new DomainException(ErrorCode.NETWORK,
                "  provider said no  ");
        assertEquals("provider said no", ChatFailure.detail(error));
    }

    @Test
    public void emptyDetailFallsBackToTheErrorCode() {
        DomainException error = new DomainException(ErrorCode.AUTH, "");
        assertEquals("AUTH", ChatFailure.detail(error));
    }
}
