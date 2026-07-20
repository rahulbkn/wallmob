function format(scope, message, meta) {
    return meta ? `[${scope}] ${message} ${JSON.stringify(meta)}` : `[${scope}] ${message}`;
}
export function createLogger(scope) {
    return {
        info: (message, meta) => console.log(format(scope, message, meta)),
        warn: (message, meta) => console.warn(format(scope, message, meta)),
        error: (message, meta) => console.error(format(scope, message, meta)),
        debug: (message, meta) => console.debug(format(scope, message, meta))
    };
}
