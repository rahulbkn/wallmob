export class VideoDeleteController {
    deleteService;
    constructor(deleteService) {
        this.deleteService = deleteService;
    }
    handle = async (req, res, next) => {
        try {
            // Accept token from JSON body or query — some runtimes strip DELETE bodies.
            const fromBody = typeof req.body?.ownerToken === "string" ? req.body.ownerToken : undefined;
            const fromQuery = typeof req.query?.ownerToken === "string" ? req.query.ownerToken : undefined;
            const ownerToken = fromBody || fromQuery;
            await this.deleteService.delete(req.params.id, ownerToken);
            res.json({ success: true });
        }
        catch (error) {
            next(error);
        }
    };
}
