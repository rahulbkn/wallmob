export class AppError extends Error {
    status;
    code;
    details;
    constructor(message, status = 500, code = "INTERNAL_ERROR", details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
        this.name = this.constructor.name;
    }
}
export class BadRequestError extends AppError {
    constructor(message, details) {
        super(message, 400, "BAD_REQUEST", details);
    }
}
export class NotFoundError extends AppError {
    constructor(message, details) {
        super(message, 404, "NOT_FOUND", details);
    }
}
export class ForbiddenError extends AppError {
    constructor(message, details) {
        super(message, 403, "FORBIDDEN", details);
    }
}
export class UpstreamServiceError extends AppError {
    constructor(message, details) {
        super(message, 502, "UPSTREAM_ERROR", details);
    }
}
export class ServerConfigError extends AppError {
    constructor(message, details) {
        super(message, 500, "SERVER_CONFIG_ERROR", details);
    }
}
/** Thrown by a StorageProvider stub that hasn't been implemented yet. */
export class ProviderNotImplementedError extends AppError {
    constructor(providerName) {
        super(`Storage provider "${providerName}" is registered but not yet implemented.`, 501, "PROVIDER_NOT_IMPLEMENTED");
    }
}
