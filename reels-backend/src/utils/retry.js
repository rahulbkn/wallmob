export async function withRetry(fn, options = {}) {
    const { retries = 3, baseDelayMs = 1000, shouldRetry = () => true } = options;
    let lastError;
    for (let attempt = 1; attempt <= retries; attempt++) {
        try {
            return await fn();
        }
        catch (error) {
            lastError = error;
            const canRetry = attempt < retries && shouldRetry(error, attempt);
            if (!canRetry)
                break;
            const delay = baseDelayMs * 2 ** (attempt - 1);
            await new Promise((resolve) => setTimeout(resolve, delay));
        }
    }
    throw lastError;
}
