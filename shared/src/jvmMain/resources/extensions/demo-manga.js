class DefaultExtension extends MProvider {
    get supportsLatest() {
        return true;
    }

    getHeaders(url) {
        return {};
    }

    async getPopular(page) {
        return {
            list: [
                {
                    name: "Demo Manga",
                    link: "https://demo.nyora/manga/demo-manga",
                    imageUrl: "https://picsum.photos/id/1069/400/600",
                    description: "A built-in desktop demo source.",
                    author: "Nyora Desktop",
                    artist: "Nyora Desktop",
                    genre: ["Demo", "Sample"],
                    status: 0,
                },
            ],
            hasNextPage: false,
        };
    }

    async getLatestUpdates(page) {
        return this.getPopular(page);
    }

    async search(query, page, filters) {
        return {
            list: [
                {
                    name: query + " Result",
                    link: "https://demo.nyora/manga/" + encodeURIComponent(query),
                    imageUrl: "https://picsum.photos/id/1074/400/600",
                    description: "Search result for " + query,
                    author: "Nyora Desktop",
                    artist: "Nyora Desktop",
                    genre: ["Search"],
                    status: 0,
                },
            ],
            hasNextPage: false,
        };
    }

    async getDetail(url) {
        return {
            name: "Demo Manga",
            link: url,
            imageUrl: "https://picsum.photos/id/1069/400/600",
            description: "A built-in desktop demo source.",
            author: "Nyora Desktop",
            artist: "Nyora Desktop",
            genre: ["Demo", "Sample"],
            status: 0,
            chapters: [
                {
                    name: "Chapter 2",
                    url: "https://picsum.photos/id/1035/900/1300",
                },
                {
                    name: "Chapter 1",
                    url: "https://picsum.photos/id/1015/900/1300",
                },
            ],
        };
    }

    async getPageList(url) {
        return [
            {
                url: "https://picsum.photos/id/1015/900/1300",
                headers: {},
            },
            {
                url: "https://picsum.photos/id/1025/900/1300",
                headers: {},
            },
            {
                url: "https://picsum.photos/id/1035/900/1300",
                headers: {},
            },
        ];
    }

    getFilterList() {
        return [];
    }

    getSourcePreferences() {
        return [];
    }
}

extention = new DefaultExtension();
