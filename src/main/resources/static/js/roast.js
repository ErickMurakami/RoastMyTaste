(() => {
    'use strict';

    let pending = false;
    const workspace = () => document.getElementById('roast-workspace');

    function textElement(tag, className, value) {
        const element = document.createElement(tag);
        element.className = className;
        element.textContent = value;
        return element;
    }

    function spotifyImageUrl(value) {
        if (typeof value !== 'string') return null;
        try {
            const url = new URL(value);
            const spotifyHost = url.hostname.endsWith('.scdn.co') || url.hostname.endsWith('.spotifycdn.com');
            return url.protocol === 'https:' && spotifyHost && !url.username && !url.password ? url.href : null;
        } catch {
            return null;
        }
    }

    function listeningSlide(item, index, total, kind) {
        const slide = document.createElement('li');
        slide.className = 'carousel-slide';
        slide.setAttribute('aria-roledescription', 'slide');
        slide.setAttribute('aria-label', `${index + 1} of ${total}`);
        const link = document.createElement('a');
        link.className = 'listening-card';
        link.href = `https://open.spotify.com/${kind === 'tracks' ? 'track' : 'artist'}/${encodeURIComponent(item.id)}`;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        link.setAttribute('aria-label', `${item.name} — open in Spotify (new tab)`);

        const number = String(index + 1).padStart(2, '0');
        const artwork = document.createElement('span');
        artwork.className = `listening-art${kind === 'artists' ? ' artist-art' : ''}`;
        artwork.setAttribute('aria-hidden', 'true');
        artwork.append(textElement('span', '', kind === 'artists' ? [...item.name.trim()][0] || '♪' : number));
        const imageUrl = spotifyImageUrl(item.imageUrl);
        if (imageUrl) {
            const image = document.createElement('img');
            image.src = imageUrl;
            image.alt = '';
            image.width = 72;
            image.height = 72;
            image.loading = 'lazy';
            image.decoding = 'async';
            image.referrerPolicy = 'no-referrer';
            image.addEventListener('load', () => artwork.classList.add('has-image'), { once: true });
            image.addEventListener('error', () => {
                artwork.classList.remove('has-image');
                image.remove();
            }, { once: true });
            artwork.append(image);
        }

        const details = document.createElement('span');
        details.className = 'listening-details';
        details.append(textElement('span', 'listening-rank', `NO. ${number}`));
        details.append(textElement('strong', 'listening-name', item.name));
        const credits = kind === 'tracks' ? item.artistsNames : item.genres;
        if (Array.isArray(credits)) {
            details.append(textElement('span', 'listening-description', credits.filter(value => typeof value === 'string').slice(0, 3).join(' · ')));
        }
        details.append(textElement('span', 'listening-link-label', 'Open in Spotify ↗'));
        link.append(artwork, details);
        slide.append(link);
        return slide;
    }

    function initialiseCarousel(root) {
        const kind = root.dataset.carousel;
        const list = root.querySelector('.carousel-list');
        const status = root.querySelector('.preview-status');
        const position = root.querySelector('.carousel-position');
        const retry = root.querySelector('.preview-retry');
        const reconnect = root.querySelector('.preview-reconnect');
        const previous = root.querySelector('[data-step="-1"]');
        const next = root.querySelector('[data-step="1"]');
        let index = 0;
        let loading = false;
        let transition;

        function showSlide(selected, animate = true) {
            if (!list.children.length) return;
            const focusInSlides = list.contains(document.activeElement);
            const previousIndex = index;
            index = Math.max(0, Math.min(selected, list.children.length - 1));
            transition?.cancel();
            [...list.children].forEach((slide, slideIndex) => { slide.hidden = slideIndex !== index; });
            const card = list.children[index].querySelector('.listening-card');
            if (animate && index !== previousIndex && card.animate
                    && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
                const offset = index > previousIndex ? 14 : -14;
                transition = card.animate([
                    { opacity: 0, transform: `translateX(${offset}px)` },
                    { opacity: 1, transform: 'translateX(0)' }
                ], { duration: 230, easing: 'cubic-bezier(.2,.7,.2,1)' });
            }
            position.textContent = `${String(index + 1).padStart(2, '0')} / ${list.children.length}`;
            previous.disabled = index === 0;
            next.disabled = index === list.children.length - 1;
            if (focusInSlides || (root.contains(document.activeElement) && document.activeElement.disabled)) {
                list.focus({ preventScroll: true });
            }
        }

        root.querySelectorAll('[data-step]').forEach(button => {
            button.addEventListener('click', () => showSlide(index + Number(button.dataset.step)));
        });
        root.addEventListener('keydown', event => {
            if (!list.children.length) return;
            const positions = { ArrowLeft: index - 1, ArrowRight: index + 1, Home: 0, End: list.children.length - 1 };
            if (Object.hasOwn(positions, event.key)) {
                event.preventDefault();
                showSlide(positions[event.key]);
            }
        });

        async function load() {
            if (loading) return;
            loading = true;
            status.hidden = false;
            retry.hidden = true;
            reconnect.hidden = true;
            list.setAttribute('aria-busy', 'true');
            status.querySelector('p').textContent = kind === 'tracks' ? 'Gathering the track record…' : 'Rounding up the artists…';
            const abort = new AbortController();
            const timeout = window.setTimeout(() => abort.abort(), 30000);
            try {
                const response = await fetch(root.dataset.source, {
                    credentials: 'same-origin',
                    headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
                    signal: abort.signal,
                    redirect: 'manual'
                });
                if (response.type === 'opaqueredirect' || response.status === 401 || response.status === 403) {
                    const expired = new Error('expired-session');
                    expired.reconnect = true;
                    throw expired;
                }
                if (!response.ok) throw new Error('preview-unavailable');
                const items = await response.json();
                if (!Array.isArray(items) || !items.every(item => item && typeof item.id === 'string' && typeof item.name === 'string')) {
                    throw new Error('invalid-preview');
                }
                list.replaceChildren(...items.map((item, itemIndex) => listeningSlide(item, itemIndex, items.length, kind)));
                if (items.length) {
                    status.hidden = true;
                    showSlide(0, false);
                } else {
                    status.querySelector('p').textContent = `No top ${kind} yet. Give Spotify a little more listening history.`;
                    retry.hidden = false;
                }
            } catch (error) {
                status.querySelector('p').textContent = error.reconnect
                    ? 'Your Spotify connection needs an encore.'
                    : `We could not load your ${kind} right now. You can still try generating a roast.`;
                reconnect.hidden = !error.reconnect;
                retry.hidden = Boolean(error.reconnect);
            } finally {
                window.clearTimeout(timeout);
                list.setAttribute('aria-busy', 'false');
                loading = false;
            }
        }

        retry.addEventListener('click', load);
        load();
    }

    if (window.fetch && window.AbortController) {
        document.querySelectorAll('[data-carousel]').forEach(initialiseCarousel);
    } else {
        document.querySelectorAll('.preview-status p').forEach(message => {
            message.textContent = 'This browser cannot load the listening preview. You can still generate your roast.';
        });
        document.querySelectorAll('.carousel-list').forEach(list => list.setAttribute('aria-busy', 'false'));
    }

    function setLoading(loading) {
        pending = loading;
        const root = workspace();
        root.querySelectorAll('button[type="submit"], select').forEach(element => {
            element.disabled = loading;
        });
        root.querySelector('.submit-label').textContent = loading ? 'Roasting…'
            : (document.getElementById('roast-result') ? 'Roast Again' : 'Roast My Taste');
        document.getElementById('result-column').setAttribute('aria-busy', String(loading));
        document.getElementById('loading-state').hidden = !loading;
        root.querySelectorAll('.empty-state, .roast-result').forEach(element => {
            element.hidden = loading;
        });
    }

    function showError(message, reconnect = false) {
        document.getElementById('request-error-message').textContent = message;
        document.getElementById('request-reconnect').hidden = !reconnect;
        const notice = document.getElementById('request-error');
        notice.hidden = false;
        notice.setAttribute('tabindex', '-1');
        notice.focus();
    }

    document.addEventListener('submit', async event => {
        const form = event.target;
        if (form.id !== 'roast-form' || !window.fetch || !window.AbortController) return;
        event.preventDefault();
        if (pending) return;

        // Capture the language and Spring's hidden CSRF token before disabling controls.
        const data = new URLSearchParams(new FormData(form));
        document.getElementById('request-error').hidden = true;
        setLoading(true);
        const abort = new AbortController();
        const timeout = window.setTimeout(() => abort.abort(), 95000);
        try {
            const response = await fetch(form.action, {
                method: 'POST',
                body: data,
                credentials: 'same-origin',
                headers: { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'text/html' },
                signal: abort.signal,
                redirect: 'error'
            });
            const html = await response.text();
            const page = new DOMParser().parseFromString(html, 'text/html');
            const next = page.getElementById('roast-workspace');
            if (next) {
                // Only accept our server-rendered view. AI fields are escaped by Thymeleaf th:text.
                workspace().replaceWith(next);
                const target = next.querySelector('.notice:not([hidden]), #roast-result');
                if (target) {
                    target.setAttribute('tabindex', '-1');
                    target.focus();
                }
            } else if (response.status === 401 || response.status === 403) {
                showError('Your session has expired. Reconnect with Spotify to keep going.', true);
            } else {
                showError('Something hit a wrong note. Please try again in a moment.');
            }
        } catch (error) {
            showError(error.name === 'AbortError'
                ? 'Our critic is taking too long. Please wait a moment before trying again.'
                : 'We lost the connection. Check your internet connection and try again.');
        } finally {
            window.clearTimeout(timeout);
            setLoading(false);
        }
    });
})();
