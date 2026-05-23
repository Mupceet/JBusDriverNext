(function() {
    'use strict';

    // 1. Set viewport meta for mobile
    var viewport = document.querySelector('meta[name="viewport"]');
    if (!viewport) {
        viewport = document.createElement('meta');
        viewport.name = 'viewport';
        document.head.appendChild(viewport);
    }
    viewport.content = 'width=device-width,initial-scale=1.0,maximum-scale=1.0,minimum-scale=1.0,user-scalable=no';

    // 2. Remove ads
    document.querySelectorAll('.bcpic2, .a_pt, .a_pb').forEach(function(el) { el.remove(); });

    // 3. Remove hidden container
    var hiddenDiv = document.querySelector('div.wp.cl');
    if (hiddenDiv) hiddenDiv.remove();

    // 4. Clean top bar
    var nav = document.getElementById('toptb');
    if (nav) {
        var logo = nav.querySelector('a.jav-logo');
        if (logo) logo.remove();
        var wp = nav.querySelector('div.wp');
        if (wp) wp.remove();
        var member = nav.querySelector('div.login-wrap.y');
        if (member) {
            var memberName = member.querySelector('span.member-name');
            if (memberName) memberName.remove();
            var angle = member.querySelector('span.angle');
            if (angle) angle.remove();
        }
    }

    // 5. Flatten login menu
    var member = nav ? nav.querySelector('div.login-wrap.y') : null;
    if (member) {
        var menuBody = member.querySelector('div.menu-body');
        if (menuBody) {
            var menu = document.createElement('ul');
            menu.style.cssText = 'display:flex;justify-content:left;align-items:center;list-style:none;padding:0;margin:0;';
            menuBody.querySelectorAll('div.item a').forEach(function(a) {
                var li = document.createElement('li');
                li.style.cssText = 'display:inline-block;margin:0 8px;';
                a.style.cssText = 'font-size:14px;padding:10px 0;text-align:center;color:#c0b0e0;';
                li.appendChild(a);
                menu.appendChild(li);
            });
            nav.appendChild(menu);
            if (menu.firstElementChild) menu.firstElementChild.remove();
            menuBody.remove();
        }
    }

    // 6. Adjust back-to-top button
    var backBtn = document.getElementsByClassName('biaoqi-fix-area');
    if (backBtn[0]) {
        backBtn[0].style.cssText = 'left:0;margin-left:80%;';
        if (backBtn[0].firstElementChild) {
            backBtn[0].firstElementChild.style.bottom = '10%';
        }
    }

    // 7. Enlarge touch targets
    document.querySelectorAll('#threadlisttableid tbody').forEach(function(tbody) {
        tbody.querySelectorAll('th a, .post_infolist a').forEach(function(a) {
            a.style.padding = '8px 4px';
            a.style.display = 'inline-block';
        });
    });

    // 8. Post images: skip smileys
    document.querySelectorAll('.t_f img').forEach(function(img) {
        if (!img.src.includes('/static/image/smiley/')) {
            img.style.width = '100%';
            img.style.height = 'auto';
        }
    });
})();
