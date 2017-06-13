import('./spime.js').then(Spime => {

    function itemURL(id, path) {
        return encodeURIComponent(id) + '/' + path;
    }

    $.get('/logo.html', (x) => {
        $('#logo').html(x);
    });

    const query = $('#query');
    const facets = $('#facets');
    const queryText = $('#query_text');
    const suggestions = $('#query_suggestions');

    const onQueryTextChanged = _.throttle(() => {
        const qText = queryText.val();
        if (!qText.length) {
            suggestions.html('');
            return;
        }

        //$('#query_status').html('Suggesting: ' + qText);

        $.get('/suggest', {q: qText}, function (result) {

            if (!result.length) {
                suggestions.html('');
            } else {
                suggestions.html(_.map((result), (x) =>
                    D('suggestion').text(x).click(() => {
                        queryText.val(x);
                        update(x);
                    })
                ));
            }
        });
    }, 100, true, true);

    const querySubmit = () => {
        update(queryText.val());
    };

    queryText.on('input', onQueryTextChanged);

    queryText.on('keypress', (e) => {
        if (e.keyCode === 13)
            querySubmit();
    });

    function scrollTop() {
        $("body").scrollTop(0);
    }

    function expand() {
        query.addClass('expand');
//            $('#menu').removeClass('sidebar');
//            $('#menu').addClass('expand');
//            $('#results').hide();
//
//            $('#facets .list-item').removeClass('list-item').addClass('grid-item');

    }

    function contract() {
        query.removeClass('expand');
//            unfocus();
//            $('#results').show().addClass('main');
//            $('#menu').removeClass('expand');
//            $('#menu').addClass('sidebar');
//
//            $('#facets .grid-item').removeClass('grid-item').attr('style', '').addClass('list-item');
    }

    function focus(id) {
        //Backbone.history.navigate("the/" + encodeURIComponent(id), { trigger: true });

        window.open(itemURL(id, 'data'), '_blank', 'menubar=no,status=no,titlebar=no');
    }


    function suggestionsClear() {
        suggestions.html('');
    }

    function update(qText) {

        Backbone.history.navigate("all/" + encodeURIComponent(qText), {trigger: true});

    }

    function facet(v) {

        const id = v[0]
            .replace(/_/g, ' ')
            .replace(/\-/g, ' ')
        ; //HACK
        const score = v[1];

        return E('button', 'facet')
            .text(id)
            .attr('style',
                'font-size:' + (50.0 + 20 * (Math.log(1.0 + score)) ) + '%'
            )
            .click(() => {
                queryText.val(/* dimension + ':' + */ id);
                querySubmit();
                return false;
            });
    };

    function loadFacets(result) {
        facets.html(_.map(result, facet));


        //setTimeout(()=>{

//            setTimeout(()=>{ facets.packery('layout');
//
//                setTimeout(()=>{ facets.packery('layout'); }, 300);
//
//            }, 300);
        //}, 0);
    }

//PACKERY.js
//http://codepen.io/desandro/pen/vKjAPE/
//http://packery.metafizzy.co/extras.html#animating-item-size

    function updateFacet(dimension) {

        //const klass = label;

//            $('#facets.' + klass).remove();
//
//            const f = $('<svg width="250" height="250">').attr('class', klass);//.html(label + '...');
//            $('#facets').append($('<div>').append(f));

        FACETS({q: dimension}, loadFacets);

    }


//START ----------------->
    const $results = $('#results');

    const Router = Backbone.Router.extend({

        routes: {
            "": "start",
            "all/:query": "all",
            "the/:query": "the"
        },

//            the: function(id) {
//
//                suggestionsClear();
//
//
//
////                $('#focus').attr('class', 'main').html(
////                    E('iframe').attr('src', dataURL).attr('width', '100%').attr('height', '100%')
////                );
////                $('#results').attr('class', 'sidebar shiftdown');
////                $('#menu').attr('class', 'hide');
////                $('#focus').show();
//
//            },

        all: function (qText) {

            suggestionsClear();

            contract();

            scrollTop();

            //$('#query_status').html('').append($('<p>').text('Query: ' + qText));
            $results.html('Searching...');

            $.get('/find', {q: qText}, function (result) {

                let ss, rr, ff;
                try {
                    ss = (result);
                    rr = ss[0]; //first part: search results
                    ff = ss[1]; //second part: facets
                } catch (e) {
                    //usually just empty search result
                    $results.html('No matches for: "' + qText + '"');
                    return;
                }

                contract();

                loadFacets(ff);

                const clusters = {};

                $results.html(
                    _.map(rr, (x) => {

                        if (!x.I)
                            return;

                        const y = D('list-item result');
                        y.data('o', x);

                        let I = x.I;
                        if (x.inh) {
                            x.out = x.inh['>'];

                            const vin = x.inh['<'];
                            if (vin && !(vin.length === 1 && vin[0].length === 0)) { //exclude root tag
                                x['in'] = vin;
                                I = vin;
                            }
                        }


                        if (clusters[I] === undefined) {
                            clusters[I] = [y];
                        } else {
                            clusters[I].push(y);
                        }


                        const header = D('header');

                        header.append(
                            E('h2').text(x.N || x.I)
                        );


                        const meta = D('meta');


                        y.append(
                            header,
                            meta
                        );

                        if (x.icon) {
                            const tt =
                                    //E('a').attr('class', 'fancybox').attr('rel', 'group').append(
                                    E('img').attr('src', itemURL(I, 'icon'))
                                //)
                            ;
                            y.append(
                                tt
                            );

                            //http://fancyapps.com/fancybox/#examples
                            //tt.fancybox();
                        }

                        if (x['_']) {
                            y.append(E('p').attr('class', 'textpreview').html(x['_'].replace('\n', '<br/>')));
                        }


                        if (x.data) {
                            y.click(() => {
                                focus(x.data);
                            });
                        }


                        return y;
                    })
                );

                _.each(clusters, (c, k) => {

                    if (c.length < 2)
                        return; //ignore clusters of length < 2

                    const start = c[0];

                    const d = D('list-item result');
                    $(start).before(d);
                    c.forEach(cc => {
                        /* {

                         d = cc;
                         } else {
                         children.push(cc);
                         }*/
                        cc.detach();
                        cc.addClass('sub');
                        if (cc.data('o').I !== k) //the created root entry for this cluster, ignore for now
                            d.append(cc);
                    });

                    //HACK if there was only 1 child, just pop it back to top-level subsuming any parents
                    const dc = d.children();
                    if (dc.length === 1) {
                        $(dc[0]).removeClass('sub');
                        d.replaceWith(dc[0]);
                    }


                });


            });

        },

        start: function () {

            facets.html('');

            expand();

            //updateFacet('I', 'Category');
            updateFacet('>', 'Tag');


        }


    });

    const router = new Router();

    Backbone.history.start();

});