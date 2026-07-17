export class VideoFeedController {
    feedService;
    constructor(feedService) {
        this.feedService = feedService;
    }
    getFeed = async (req, res, next) => {
        try {
            const page = Math.max(1, parseInt(String(req.query.page || "1")) || 1);
            const perPage = Math.min(50, Math.max(1, parseInt(String(req.query.perPage || "10")) || 10));
            const category = req.query.category ? String(req.query.category) : undefined;
            const uploader = req.query.uploader ? String(req.query.uploader) : undefined;
            const result = await this.feedService.getFeed({ page, perPage, category, uploader });
            res.json({ success: true, ...result });
        }
        catch (error) {
            next(error);
        }
    };
    getById = async (req, res, next) => {
        try {
            const video = await this.feedService.getById(req.params.id);
            res.json({ success: true, data: video });
        }
        catch (error) {
            next(error);
        }
    };
    recordView = async (req, res, next) => {
        try {
            await this.feedService.recordView(req.params.id);
            res.json({ success: true });
        }
        catch (error) {
            next(error);
        }
    };
    like = async (req, res, next) => {
        try {
            await this.feedService.like(req.params.id);
            res.json({ success: true });
        }
        catch (error) {
            next(error);
        }
    };
    share = async (req, res, next) => {
        try {
            await this.feedService.share(req.params.id);
            res.json({ success: true });
        }
        catch (error) {
            next(error);
        }
    };
}
