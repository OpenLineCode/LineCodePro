package cn.lineai.engine.agent.loop;

import java.util.List;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.tool.api.ToolCall;

/** Inputs the decider reacts to. */
public interface ToolLoopEvent {

    final class Start implements ToolLoopEvent { }

    final class ResponseReady implements ToolLoopEvent {
        public final List<ToolCall> calls;

        public ResponseReady(List<ToolCall> calls) {
            this.calls = calls == null ? java.util.Collections.<ToolCall>emptyList() : calls;
        }
    }

    final class ConfirmAnswered implements ToolLoopEvent {
        public final boolean allowed;

        public ConfirmAnswered(boolean allowed) {
            this.allowed = allowed;
        }
    }

    final class ToolsFinished implements ToolLoopEvent { }

    final class Cancel implements ToolLoopEvent { }

    final class Failed implements ToolLoopEvent {
        /** Failure to report; its code selects the UI message, so it must survive the loop. */
        public final DomainException error;

        /**
         * Creates a terminal failure event.
         *
         * @param error failure reported by the model client or a tool; null becomes an
         *              {@link ErrorCode#UNKNOWN} failure with no detail
         */
        public Failed(DomainException error) {
            this.error = error == null
                    ? new DomainException(ErrorCode.UNKNOWN, "") : error;
        }
    }
}
