export class AppError extends Error {
  constructor(
    message: string,
    public readonly status: number = 500,
    public readonly code: string = "INTERNAL_ERROR",
    public readonly details?: unknown
  ) {
    super(message);
    this.name = this.constructor.name;
  }
}

export class BadRequestError extends AppError {
  constructor(message: string, details?: unknown) {
    super(message, 400, "BAD_REQUEST", details);
  }
}

export class NotFoundError extends AppError {
  constructor(message: string, details?: unknown) {
    super(message, 404, "NOT_FOUND", details);
  }
}

export class ForbiddenError extends AppError {
  constructor(message: string, details?: unknown) {
    super(message, 403, "FORBIDDEN", details);
  }
}

export class ConflictError extends AppError {
  constructor(message: string, details?: unknown) {
    super(message, 409, "CONFLICT", details);
  }
}

export class UpstreamServiceError extends AppError {
  constructor(message: string, details?: unknown) {
    super(message, 502, "UPSTREAM_ERROR", details);
  }
}

export class ServerConfigError extends AppError {
  constructor(message: string, details?: unknown) {
    super(message, 500, "SERVER_CONFIG_ERROR", details);
  }
}

/** Thrown by a StorageProvider stub that hasn't been implemented yet. */
export class ProviderNotImplementedError extends AppError {
  constructor(providerName: string) {
    super(`Storage provider "${providerName}" is registered but not yet implemented.`, 501, "PROVIDER_NOT_IMPLEMENTED");
  }
}
