package cn.lineai.runtime;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.shell.TerminalProviderGateway;

/** Blocking file helpers over the active terminal provider workspace. */
final class TerminalWorkspace {

    /** Not instantiable; every helper is static. */
    private TerminalWorkspace() {
    }

    /** Display label of the terminal provider workspace. */
    static String label(TerminalProviderGateway gateway) {
        String name = gateway.providerName();
        return name.isEmpty() ? "Terminal" : name;
    }

    /**
     * Lists the provider home as {@code D path}/{@code F path} lines; blocking, callers use io.
     *
     * @param gateway bound terminal provider gateway
     * @return one line per entry, or an empty string when nothing is listed
     */
    static String list(TerminalProviderGateway gateway) {
        return gateway.execute(
                "find . -maxdepth 4 -type d -print 2>/dev/null | sed 's/^/D /'; "
                        + "find . -maxdepth 4 -type f -print 2>/dev/null | sed 's/^/F /'",
                30_000);
    }

    /**
     * Reads one provider-home-relative file; blocking, callers use io.
     *
     * @param gateway bound terminal provider gateway
     * @param relativePath path relative to the provider home
     * @return UTF-8 content reported by the provider shell
     */
    static String read(TerminalProviderGateway gateway, String relativePath) {
        String path = relativePath == null ? "" : relativePath.trim();
        if (path.isEmpty()) {
            throw new DomainException(ErrorCode.INVALID_INPUT, "empty file path");
        }
        return gateway.execute("cat -- " + quote(path), 30_000);
    }

    /** Quotes one shell argument for the POSIX provider shell. */
    private static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
