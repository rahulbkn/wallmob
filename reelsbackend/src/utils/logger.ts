export interface Logger {
  info(message: string, meta?: Record<string, unknown>): void;
  warn(message: string, meta?: Record<string, unknown>): void;
  error(message: string, meta?: Record<string, unknown>): void;
  debug(message: string, meta?: Record<string, unknown>): void;
}

function format(scope: string, message: string, meta?: Record<string, unknown>): string {
  return meta ? `[${scope}] ${message} ${JSON.stringify(meta)}` : `[${scope}] ${message}`;
}

export function createLogger(scope: string): Logger {
  return {
    info: (message, meta) => console.log(format(scope, message, meta)),
    warn: (message, meta) => console.warn(format(scope, message, meta)),
    error: (message, meta) => console.error(format(scope, message, meta)),
    debug: (message, meta) => console.debug(format(scope, message, meta))
  };
}
