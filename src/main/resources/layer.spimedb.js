class SpimeDBLayer extends GeoLayer {
    constructor(name, url) {
        super(name, new WorldWind.RenderableLayer(name));
        this.url = url;
        this.socket = undefined;
    }

    start(f) {
        // Set up the common placemark attributes.
        const placemarkAttributes = new WorldWind.PlacemarkAttributes(null);
        placemarkAttributes.imageScale = 1;
        placemarkAttributes.imageColor = WorldWind.Color.WHITE;
        placemarkAttributes.labelAttributes.offset = new WorldWind.Offset(
            WorldWind.OFFSET_FRACTION, 0.5,
            WorldWind.OFFSET_FRACTION, 1.5);
        placemarkAttributes.imageSource = WorldWind.configuration.baseUrl + "images/white-dot.png";

        const shapeConfigurationCallback = function (geometry, properties) {
            const cfg = {};
            //console.log(geometry, properties);
            if (geometry.isPointType() || geometry.isMultiPointType()) {
                cfg.attributes = new WorldWind.PlacemarkAttributes(placemarkAttributes);

                if (properties && (properties.name || properties.Name || properties.NAME)) {
                    cfg.name = properties.name || properties.Name || properties.NAME;
                }

                //AirNow HACK
                if (properties.SiteName)
                    cfg.name = properties.SiteName + "\n" + properties.PM_AQI_LABEL;

                if (properties && properties.POP_MAX) {
                    const population = properties.POP_MAX;
                    cfg.attributes.imageScale = 0.01 * Math.log(population);
                }

            } else if (geometry.isLineStringType() || geometry.isMultiLineStringType()) {
                cfg.attributes = new WorldWind.ShapeAttributes(null);
                cfg.attributes.drawOutline = true;
                cfg.attributes.outlineColor = new WorldWind.Color(
                    0.1 * cfg.attributes.interiorColor.red,
                    0.3 * cfg.attributes.interiorColor.green,
                    0.7 * cfg.attributes.interiorColor.blue,
                    1.0);
                cfg.attributes.outlineWidth = 2.0;
            } else if (geometry.isPolygonType() || geometry.isMultiPolygonType()) {
                cfg.attributes = new WorldWind.ShapeAttributes(null);

                // Fill the polygon with a random pastel color.
                cfg.attributes.interiorColor = new WorldWind.Color(
                    0.375 + 0.5 * Math.random(),
                    0.375 + 0.5 * Math.random(),
                    0.375 + 0.5 * Math.random(),
                    0.5);
                // Paint the outline in a darker variant of the interior color.
                cfg.attributes.outlineColor = new WorldWind.Color(
                    0.5 * cfg.attributes.interiorColor.red,
                    0.5 * cfg.attributes.interiorColor.green,
                    0.5 * cfg.attributes.interiorColor.blue,
                    1.0);
            }

            return cfg;
        };


        this.reconnect();

        // const g = new WorldWind.GeoJSONParser(this.url);
        // g.load(null, shapeConfigurationCallback, this.layer);

        super.start(f);
    }

    reconnect() {
        if (this.socket) {
            this.close();
        }

        this.socket = new WebSocket(this.url);
        this.socket.onopen = () => {
            this.socket.send("{ '_': 'index' }");
        };
        // socket.addEventListener('close', closeConnection);
        this.socket.onmessage = (x) => {
            console.log(x.data);
        };
        this.socket.onclose = (e) => {
            this.close();
        };
        this.socket.onerror = (e) => {
            console.error(e);
        };
    }


    close() {
        this.socket.close();
        delete this.socket;
    }

// changeConnection(event) {
    //     // open the connection if it's closed, or close it if open:
    //     if (socket.readyState === WebSocket.CLOSED) {
    //         openSocket(serverURL);
    //     } else {
    //         socket.close();
    //     }
    // }


    // closeConnection() {
    //     this.socket.close();
    // }


    // function sendMessage() {
    //     //if the socket's open, send a message:
    //     if (socket.readyState === WebSocket.OPEN) {
    //         socket.send(outgoingText.value);
    //     }
    // }

    stop(focus) {
        super.stop(focus);
        this.layer.removeAllRenderables();
    }

}
