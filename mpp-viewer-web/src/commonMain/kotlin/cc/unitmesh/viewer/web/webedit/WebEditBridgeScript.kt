package cc.unitmesh.viewer.web.webedit

/**
 * JavaScript code to inject into WebView for DOM selection and communication
 *
 * This script provides:
 * - Inspect mode with Shadow DOM support
 * - Visual overlay highlighting (isolated via Shadow DOM)
 * - DOM tree extraction with shadow roots
 * - MutationObserver for real-time updates
 * - Bidirectional communication with Kotlin via kmpJsBridge
 */
fun getWebEditBridgeScript(): String = """
(function() {
    console.log('[WebEditBridge] Script injection starting...');
    
    // Prevent multiple injections
    if (window.webEditBridge) {
        console.log('[WebEditBridge] Already injected, skipping');
        return;
    }
    
    console.log('[WebEditBridge] Checking kmpJsBridge availability:', typeof window.kmpJsBridge);
    
    // ========== Shadow DOM Inspect Overlay ==========
    console.log('[WebEditBridge] Creating overlay host...');
    // Create isolated overlay container using Shadow DOM
    const overlayHost = document.createElement('div');
    overlayHost.id = 'webedit-inspect-overlay-host';
    overlayHost.style.cssText = 'position: fixed; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 2147483647;';

    const shadowRoot = overlayHost.attachShadow({ mode: 'open' });
    shadowRoot.innerHTML = `
        <style>
            :host {
                all: initial;
            }
            .highlight-box {
                position: absolute;
                pointer-events: none;
                box-sizing: border-box;
                transition: all 0.1s ease;
            }
            .highlight-hover {
                border: 2px solid #2196F3;
                background: rgba(33, 150, 243, 0.1);
            }
            .highlight-selected {
                border: 3px solid #4CAF50;
                background: rgba(76, 175, 80, 0.15);
            }
            .highlight-label {
                position: absolute;
                top: -22px;
                left: 0;
                background: #2196F3;
                color: white;
                padding: 2px 6px;
                font-size: 11px;
                font-family: monospace;
                border-radius: 2px;
                white-space: nowrap;
                max-width: 300px;
                overflow: hidden;
                text-overflow: ellipsis;
            }
        </style>
        <div id="hover-box" class="highlight-box highlight-hover" style="display: none;">
            <div class="highlight-label"></div>
        </div>
        <div id="selected-box" class="highlight-box highlight-selected" style="display: none;">
            <div class="highlight-label"></div>
        </div>
    `;

    document.body.appendChild(overlayHost);

    const hoverBox = shadowRoot.getElementById('hover-box');
    const selectedBox = shadowRoot.getElementById('selected-box');

    // ========== Shadow DOM Utilities ==========
    // Query all elements including shadow DOM
    function querySelectorAllDeep(selector, root = document) {
        const elements = [];

        function traverse(node) {
            if (node.nodeType === Node.ELEMENT_NODE) {
                if (node.matches && node.matches(selector)) {
                    elements.push(node);
                }

                // Traverse shadow root if exists
                if (node.shadowRoot) {
                    traverse(node.shadowRoot);
                }
            }

            // Traverse children (only elements)
            const children = node.children || [];
            for (let child of children) {
                if (child.nodeType === Node.ELEMENT_NODE) {
                    traverse(child);
                }
            }
        }

        traverse(root);
        return elements;
    }

    // Get element at point (pierce shadow DOM)
    function getElementAtPoint(x, y) {
        let element = document.elementFromPoint(x, y);

        // Pierce through shadow roots
        while (element && element.shadowRoot) {
            const shadowElement = element.shadowRoot.elementFromPoint(x, y);
            if (!shadowElement || shadowElement === element) break;
            element = shadowElement;
        }

        // Ignore our own overlay
        if (element && element.closest('#webedit-inspect-overlay-host')) {
            return null;
        }

        return element;
    }

    // WebEdit Bridge object
    window.webEditBridge = {
        inspectMode: false,
        selectionMode: false,
        highlightedElement: null,
        selectedElement: null,
        mutationObserver: null,

        // ========== Inspect Mode Management ==========
        enableInspectMode: function() {
            this.inspectMode = true;
            this.selectionMode = true;
            overlayHost.style.pointerEvents = 'none';
            this.startMutationObserver();
            console.log('[WebEditBridge] Inspect mode enabled');
        },

        disableInspectMode: function() {
            this.inspectMode = false;
            this.selectionMode = false;
            this.clearHoverHighlight();
            this.clearSelection();
            this.stopMutationObserver();
            console.log('[WebEditBridge] Inspect mode disabled');
        },

        // Legacy: Enable/disable selection mode
        setSelectionMode: function(enabled) {
            if (enabled) {
                this.enableInspectMode();
            } else {
                this.disableInspectMode();
            }
        },

        // ========== Visual Highlighting ==========
        updateHighlightBox: function(element, box, label) {
            if (!element) {
                box.style.display = 'none';
                return;
            }

            const rect = element.getBoundingClientRect();
            box.style.display = 'block';
            box.style.left = rect.left + 'px';
            box.style.top = rect.top + 'px';
            box.style.width = rect.width + 'px';
            box.style.height = rect.height + 'px';

            const labelEl = box.querySelector('.highlight-label');
            if (labelEl) {
                labelEl.textContent = this.getElementLabel(element);
            }
        },

        getElementLabel: function(el) {
            const tag = el.tagName.toLowerCase();
            const id = el.id ? '#' + el.id : '';
            const classes = el.className && typeof el.className === 'string'
                ? '.' + el.className.trim().split(/\\s+/).slice(0, 2).join('.')
                : '';
            return tag + id + classes;
        },

        // Highlight a specific element by selector (supports shadow DOM)
        highlightElement: function(selector) {
            this.clearHoverHighlight();
            try {
                const el = this.findElementBySelector(selector);
                if (el) {
                    this.highlightedElement = el;
                    this.updateHighlightBox(el, hoverBox);
                    console.log('[WebEditBridge] highlightElement success:', selector);
                } else {
                    console.warn('[WebEditBridge] highlightElement: element not found:', selector);
                }
            } catch(e) {
                console.error('[WebEditBridge] highlightElement error:', e, 'selector:', selector);
            }
        },

        // Clear hover highlight only (keep selection)
        clearHoverHighlight: function() {
            this.highlightedElement = null;
            hoverBox.style.display = 'none';
        },

        // Clear selection highlight
        clearSelection: function() {
            this.selectedElement = null;
            selectedBox.style.display = 'none';
        },

        // Clear all highlights (for backward compatibility)
        clearHighlights: function() {
            this.clearHoverHighlight();
            this.clearSelection();
        },

        // ========== MutationObserver for DOM Changes with Throttled Refresh ==========
        domRefreshPending: false,
        domRefreshTimeout: null,
        DOM_REFRESH_THROTTLE: 1000, // ms

        startMutationObserver: function() {
            if (this.mutationObserver) return;
            const self = this;

            this.mutationObserver = new MutationObserver((mutations) => {
                // Update highlights if elements are still in DOM
                if (self.highlightedElement && !document.contains(self.highlightedElement)) {
                    self.clearHoverHighlight();
                }
                if (self.selectedElement && !document.contains(self.selectedElement)) {
                    self.clearSelection();
                } else if (self.selectedElement) {
                    // Update position if element moved
                    self.updateHighlightBox(self.selectedElement, selectedBox);
                }

                // Throttled DOM tree refresh
                if (!self.domRefreshPending) {
                    self.domRefreshPending = true;
                    if (self.domRefreshTimeout) {
                        clearTimeout(self.domRefreshTimeout);
                    }
                    self.domRefreshTimeout = setTimeout(function() {
                        self.domRefreshPending = false;
                        self.getDOMTree();
                        console.log('[WebEditBridge] DOM tree refreshed after mutations');
                    }, self.DOM_REFRESH_THROTTLE);
                }

                // Notify Kotlin about DOM changes
                self.sendToKotlin('DOMChanged', {
                    mutationCount: mutations.length
                });
            });

            this.mutationObserver.observe(document.body, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['class', 'id', 'style']
            });
        },

        stopMutationObserver: function() {
            if (this.domRefreshTimeout) {
                clearTimeout(this.domRefreshTimeout);
                this.domRefreshTimeout = null;
            }
            this.domRefreshPending = false;
            if (this.mutationObserver) {
                this.mutationObserver.disconnect();
                this.mutationObserver = null;
            }
        },

        // Scroll to element (supports shadow DOM)
        scrollToElement: function(selector) {
            try {
                const el = this.findElementBySelector(selector);
                if (el) {
                    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    console.log('[WebEditBridge] scrollToElement success:', selector);
                } else {
                    console.warn('[WebEditBridge] scrollToElement: element not found:', selector);
                }
            } catch(e) {
                console.error('[WebEditBridge] scrollToElement error:', e, 'selector:', selector);
            }
        },

        // ========== Browser Action Execution (for LLM automation) ==========
        // Action schema matches Kotlin WebEditAction: { action, selector?, text?, clearFirst?, value?, key?, id? }
        performAction: function(action) {
            const self = this;
            const act = (action && action.action) ? action.action : 'unknown';
            const selector = action ? (action.selector || null) : null;
            const id = action ? (action.id || null) : null;

            function send(ok, message) {
                self.sendToKotlin('ActionResult', {
                    action: act,
                    ok: !!ok,
                    selector: selector,
                    message: message || null,
                    id: id
                });
            }

            try {
                if (!action || !action.action) {
                    send(false, 'Missing action');
                    return;
                }

                if (act === 'click') {
                    const el = self.findElementBySelector(selector || '');
                    if (!el) return send(false, 'Element not found');
                    try {
                        const rect = el.getBoundingClientRect();
                        if (!rect || rect.width <= 1 || rect.height <= 1) return send(false, 'Element not visible');
                    } catch(e) { /* ignore */ }
                    try { el.scrollIntoView({ behavior: 'auto', block: 'center' }); } catch(e) { /* ignore */ }
                    try { if (el.focus) el.focus(); } catch(e) { /* ignore */ }
                    try {
                        const opts = { bubbles: true, cancelable: true, view: window };
                        el.dispatchEvent(new MouseEvent('mouseover', opts));
                        el.dispatchEvent(new MouseEvent('mousedown', opts));
                        el.dispatchEvent(new MouseEvent('mouseup', opts));
                    } catch(e) { /* ignore */ }
                    try { el.click(); } catch(e) { /* ignore */ }
                    return send(true, null);
                }

                if (act === 'type') {
                    const el = self.findElementBySelector(selector || '');
                    if (!el) return send(false, 'Element not found');
                    try {
                        const rect = el.getBoundingClientRect();
                        if (!rect || rect.width <= 1 || rect.height <= 1) return send(false, 'Element not visible');
                    } catch(e) { /* ignore */ }
                    const text = (action.text != null) ? String(action.text) : '';
                    const clearFirst = (action.clearFirst !== false);
                    try { if (el.focus) el.focus(); } catch(e) { /* ignore */ }

                    const isContentEditable = !!el.isContentEditable;
                    const hasValue = ('value' in el);

                    if (!isContentEditable && !hasValue) {
                        return send(false, 'Element is not typeable');
                    }

                    if (clearFirst) {
                        if (hasValue) {
                            try { el.value = ''; } catch(e) { /* ignore */ }
                        } else if (isContentEditable) {
                            try { el.textContent = ''; } catch(e) { /* ignore */ }
                        }
                    }

                    if (hasValue) {
                        try { el.value = String(el.value || '') + text; } catch(e) { /* ignore */ }
                        try { el.dispatchEvent(new InputEvent('input', { bubbles: true })); } catch(e) {
                            try { el.dispatchEvent(new Event('input', { bubbles: true })); } catch(_e) { /* ignore */ }
                        }
                        try { el.dispatchEvent(new Event('change', { bubbles: true })); } catch(e) { /* ignore */ }
                    } else if (isContentEditable) {
                        try { el.textContent = String(el.textContent || '') + text; } catch(e) { /* ignore */ }
                        try { el.dispatchEvent(new Event('input', { bubbles: true })); } catch(e) { /* ignore */ }
                    }

                    return send(true, null);
                }

                if (act === 'select') {
                    const el = self.findElementBySelector(selector || '');
                    if (!el) return send(false, 'Element not found');
                    if (!el.tagName || el.tagName.toLowerCase() !== 'select') return send(false, 'Element is not a select');

                    const target = (action.value != null) ? String(action.value) : '';
                    let found = false;
                    const options = Array.from(el.options || []);
                    for (let i = 0; i < options.length; i++) {
                        const opt = options[i];
                        const optValue = (opt.value != null) ? String(opt.value) : '';
                        const optText = (opt.textContent != null) ? String(opt.textContent).trim() : '';
                        if (optValue === target || optText === target) {
                            el.value = optValue;
                            found = true;
                            break;
                        }
                    }
                    if (!found) return send(false, 'Option not found');

                    try { el.dispatchEvent(new Event('input', { bubbles: true })); } catch(e) { /* ignore */ }
                    try { el.dispatchEvent(new Event('change', { bubbles: true })); } catch(e) { /* ignore */ }
                    return send(true, null);
                }

                if (act === 'pressKey') {
                    const key = (action.key != null) ? String(action.key) : '';
                    const el = selector ? self.findElementBySelector(selector) : (document.activeElement || null);
                    if (el && el.focus) {
                        try { el.focus(); } catch(e) { /* ignore */ }
                    }
                    const target = el || document;
                    try {
                        const opts = { key: key, code: key, bubbles: true, cancelable: true };
                        target.dispatchEvent(new KeyboardEvent('keydown', opts));
                        target.dispatchEvent(new KeyboardEvent('keypress', opts));
                        target.dispatchEvent(new KeyboardEvent('keyup', opts));
                    } catch(e) { /* ignore */ }

                    // Many real sites (e.g., Google) may not react to synthetic Enter key events.
                    // As a pragmatic fallback, if Enter is requested, attempt to submit the nearest form.
                    if (key === 'Enter' && el) {
                        try {
                            const form = el.form || (el.closest ? el.closest('form') : null);
                            if (form) {
                                if (form.requestSubmit) {
                                    form.requestSubmit();
                                } else if (form.submit) {
                                    form.submit();
                                }
                            }
                        } catch(e) { /* ignore */ }
                    }
                    return send(true, null);
                }

                return send(false, 'Unsupported action: ' + act);
            } catch (e) {
                return send(false, e && e.message ? e.message : 'Action error');
            }
        },

        // Find element by selector - tries ID first, then CSS selector
        findElementBySelector: function(selector) {
            // If selector starts with #, try getElementById first (more reliable)
            if (selector.startsWith('#')) {
                const idPart = selector.split(' ')[0].substring(1); // Get first ID segment
                const byId = document.getElementById(idPart);
                if (byId) {
                    // If there are more parts in the selector, use querySelector from that element
                    const rest = selector.substring(selector.indexOf(' ')).trim();
                    if (rest && rest.startsWith('>')) {
                        try {
                            const child = byId.querySelector(rest.substring(1).trim());
                            if (child) return child;
                        } catch(e) { /* fall through */ }
                    }
                    return byId;
                }
            }
            
            // Try standard querySelector (with error handling for invalid selectors)
            try {
                const el = document.querySelector(selector);
                if (el) return el;
            } catch(e) {
                console.warn('[WebEditBridge] querySelector failed:', e.message);
            }
            
            // Fall back to querySelectorAllDeep for shadow DOM
            try {
                const elements = querySelectorAllDeep(selector);
                if (elements.length > 0) return elements[0];
            } catch(e) {
                console.warn('[WebEditBridge] querySelectorAllDeep failed:', e.message);
            }
            
            return null;
        },

        // Get element at specific coordinates
        getElementAtPoint: function(x, y) {
            const el = getElementAtPoint(x, y);
            if (!el) return null;

            const rect = el.getBoundingClientRect();
            return {
                id: (typeof crypto !== 'undefined' && crypto.randomUUID) ? crypto.randomUUID() : (Date.now().toString(36) + Math.random().toString(36).slice(2, 11)),
                tagName: el.tagName.toLowerCase(),
                selector: this.getSelector(el),
                textContent: (el.textContent || '').trim().substring(0, 100),
                attributes: this.getElementAttributes(el),
                boundingBox: { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
                isShadowHost: !!el.shadowRoot
            };
        },

        // Get element attributes
        getElementAttributes: function(el) {
            const attrs = {};
            if (el.id) attrs.id = el.id;
            if (el.className && typeof el.className === 'string') attrs.class = el.className;

            // Add common attributes
            ['href', 'src', 'alt', 'title', 'type', 'name', 'value'].forEach(attr => {
                if (el.hasAttribute(attr)) {
                    attrs[attr] = el.getAttribute(attr);
                }
            });

            return attrs;
        },

        // Get unique selector for element (shadow DOM aware)
        getSelector: function(el) {
            if (el.id) return '#' + this.escapeCssSelector(el.id);
            if (el === document.body) return 'body';
            if (el === document.documentElement) return 'html';

            const path = [];
            let current = el;

            while (current && current.nodeType === Node.ELEMENT_NODE) {
                let selector = current.tagName.toLowerCase();

                if (current.id) {
                    selector = '#' + this.escapeCssSelector(current.id);
                    path.unshift(selector);
                    break;
                } else if (current.className && typeof current.className === 'string') {
                    // Split classes and escape each one, filter empty/invalid
                    const classes = current.className.trim().split(/\s+/)
                        .filter(c => c && c.length > 0)
                        .slice(0, 2)
                        .map(c => this.escapeCssSelector(c));
                    if (classes.length > 0 && classes[0]) {
                        selector += '.' + classes.join('.');
                    }
                }

                path.unshift(selector);

                // Handle shadow root boundary
                if (current.parentNode && current.parentNode.nodeType === Node.DOCUMENT_FRAGMENT_NODE) {
                    path.unshift('::shadow');
                    current = current.parentNode.host;
                } else {
                    current = current.parentNode;
                }
            }

            return path.join(' > ');
        },

        // Escape special characters in CSS selectors
        escapeCssSelector: function(str) {
            if (!str) return '';
            // Escape special CSS selector characters: . # [ ] : ( ) > + ~ * = ^ $ | \
            return str.replace(/([.#\[\]:()>+~*=^$|\\])/g, '\\$1');
        },

        // Get DOM tree with Shadow DOM support
        getDOMTree: function() {
            const self = this;

            function buildTree(el, depth, isInShadow) {
                if (depth > 8) return null;

                const rect = el.getBoundingClientRect();
                const node = {
                    id: (typeof crypto !== 'undefined' && crypto.randomUUID) ? crypto.randomUUID() : (Date.now().toString(36) + Math.random().toString(36).slice(2, 11)),
                    tagName: el.tagName.toLowerCase(),
                    selector: self.getSelector(el),
                    textContent: (el.textContent || '').trim().substring(0, 50),
                    attributes: self.getElementAttributes(el),
                    boundingBox: { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
                    children: [],
                    isShadowHost: !!el.shadowRoot,
                    inShadowRoot: isInShadow || false
                };

                // Process regular children
                Array.from(el.children).slice(0, 30).forEach(child => {
                    const childNode = buildTree(child, depth + 1, isInShadow);
                    if (childNode) node.children.push(childNode);
                });

                // Process shadow DOM children
                if (el.shadowRoot) {
                    Array.from(el.shadowRoot.children).slice(0, 30).forEach(child => {
                        const childNode = buildTree(child, depth + 1, true);
                        if (childNode) {
                            node.children.push(childNode);
                        }
                    });
                }

                return node;
            }

            const tree = buildTree(document.body, 0, false);
            this.sendToKotlin('DOMTreeUpdated', { root: tree });
        },

        // ========== D2Snap: DOM Downsampling Algorithm ==========
        // Based on: https://arxiv.org/html/2508.04412v2
        INTERACTIVE_TAGS: new Set(['a', 'button', 'input', 'select', 'textarea', 'option', 'optgroup', 'label', 'form', 'details', 'summary', 'dialog', 'menu', 'menuitem']),
        SEMANTIC_TAGS: new Set(['header', 'footer', 'nav', 'main', 'article', 'section', 'aside', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'ul', 'ol', 'li', 'dl', 'dt', 'dd', 'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td', 'caption', 'figure', 'figcaption', 'blockquote', 'pre', 'code', 'img', 'video', 'audio', 'iframe', 'embed', 'object', 'canvas', 'svg']),
        PRESERVED_ATTRS: new Set(['id', 'name', 'class', 'type', 'href', 'src', 'alt', 'title', 'value', 'placeholder', 'disabled', 'readonly', 'checked', 'selected', 'required', 'role', 'for', 'tabindex', 'contenteditable']),
        DYNAMIC_ID_PATTERNS: [
            /^(.+?)-[0-9a-f]{8,}$/,
            /^(.+?)-\d{4,}$/,
            /^(.+?)_[0-9a-f]{8,}$/,
            /^(.+?)_\d{4,}$/,
            /^:r[0-9a-z]+:$/,
            /^(.+?)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
        ],

        truncateDynamicId: function(id) {
            for (const pattern of this.DYNAMIC_ID_PATTERNS) {
                const match = id.match(pattern);
                if (match) {
                    const prefix = match[1] || '';
                    return prefix ? prefix + '-{dynamic}' : '{dynamic}';
                }
            }
            return id;
        },

        filterD2SnapAttributes: function(el) {
            const attrs = {};
            const self = this;
            
            Array.from(el.attributes).forEach(attr => {
                const name = attr.name.toLowerCase();
                if (self.PRESERVED_ATTRS.has(name) || name.startsWith('aria-') || name.startsWith('data-testid')) {
                    let value = attr.value;
                    if (name === 'id') {
                        value = self.truncateDynamicId(value);
                    } else if (name === 'class') {
                        // Keep only first 3 meaningful classes, filter utility classes
                        value = value.split(/\s+/)
                            .filter(c => c && !/^[a-z]{1,2}-\d+$/.test(c))
                            .slice(0, 3)
                            .join(' ');
                    } else {
                        value = value.substring(0, 200);
                    }
                    if (value) attrs[name] = value;
                }
            });
            return attrs;
        },

        shouldPreserveD2SnapNode: function(el, attrs, childCount) {
            const tag = el.tagName.toLowerCase();
            if (this.INTERACTIVE_TAGS.has(tag)) return true;
            if (this.SEMANTIC_TAGS.has(tag)) return true;
            if (el.shadowRoot) return true;
            if (childCount > 1) return true;
            
            const text = (el.textContent || '').trim();
            if (text.length > 2) return true;
            
            const hasAria = Object.keys(attrs).some(k => k.startsWith('aria-'));
            if (hasAria || attrs.role || attrs.id || attrs.name) return true;
            
            return false;
        },

        getD2SnapTree: function(maxDepth, maxTextLen) {
            maxDepth = maxDepth || 10;
            maxTextLen = maxTextLen || 100;
            const self = this;

            function buildD2Snap(el, depth, isInShadow) {
                if (depth > maxDepth) return null;

                const tag = el.tagName.toLowerCase();
                const attrs = self.filterD2SnapAttributes(el);
                
                // Build children first
                const children = [];
                Array.from(el.children).slice(0, 30).forEach(child => {
                    const childNode = buildD2Snap(child, depth + 1, isInShadow);
                    if (childNode) children.push(childNode);
                });
                
                // Process shadow DOM
                if (el.shadowRoot) {
                    Array.from(el.shadowRoot.children).slice(0, 30).forEach(child => {
                        const childNode = buildD2Snap(child, depth + 1, true);
                        if (childNode) children.push(childNode);
                    });
                }

                // Apply flattening rules
                if (!self.shouldPreserveD2SnapNode(el, attrs, children.length)) {
                    if (children.length === 1) return children[0]; // Flatten
                    if (children.length === 0) return null; // Remove empty
                }

                // Truncate text
                let text = (el.textContent || '').trim();
                if (text.length > maxTextLen) {
                    text = text.substring(0, maxTextLen) + '...';
                }

                return {
                    id: (typeof crypto !== 'undefined' && crypto.randomUUID) ? crypto.randomUUID() : (Date.now().toString(36) + Math.random().toString(36).slice(2, 11)),
                    tagName: tag,
                    selector: self.getSelector(el),
                    text: text || null,
                    attributes: attrs,
                    children: children,
                    isShadowHost: !!el.shadowRoot,
                    inShadowRoot: isInShadow || false
                };
            }

            const tree = buildD2Snap(document.body, 0, false);
            this.sendToKotlin('D2SnapTreeUpdated', { root: tree });
            return tree;
        },

        // ========== Accessibility Tree Extraction ==========
        // Based on: https://arxiv.org/html/2508.04412v2
        computeRole: function(el) {
            const tag = el.tagName.toLowerCase();
            const role = el.getAttribute('role');
            if (role) return role;

            const type = el.getAttribute('type');
            const roleMap = {
                'button': 'button',
                'a': el.hasAttribute('href') ? 'link' : null,
                'input': this.computeInputRole(type),
                'select': el.hasAttribute('multiple') ? 'listbox' : 'combobox',
                'textarea': 'textbox',
                'img': 'img',
                'nav': 'navigation',
                'main': 'main',
                'header': 'banner',
                'footer': 'contentinfo',
                'aside': 'complementary',
                'article': 'article',
                'section': (el.hasAttribute('aria-label') || el.hasAttribute('aria-labelledby')) ? 'region' : null,
                'form': 'form',
                'table': 'table',
                'tr': 'row',
                'th': 'columnheader',
                'td': 'cell',
                'ul': 'list',
                'ol': 'list',
                'li': 'listitem',
                'dialog': 'dialog',
                'menu': 'menu',
                'menuitem': 'menuitem',
                'details': 'group',
                'summary': 'button',
                'h1': 'heading', 'h2': 'heading', 'h3': 'heading', 'h4': 'heading', 'h5': 'heading', 'h6': 'heading',
                'progress': 'progressbar',
                'meter': 'meter',
                'option': 'option',
                'search': 'search',
                'output': 'status'
            };
            return roleMap[tag] || null;
        },

        computeInputRole: function(type) {
            const inputRoles = {
                'button': 'button', 'submit': 'button', 'reset': 'button', 'image': 'button',
                'checkbox': 'checkbox', 'radio': 'radio', 'range': 'slider',
                'number': 'spinbutton', 'search': 'searchbox', 'hidden': 'none',
                'file': 'button', 'color': 'button'
            };
            return inputRoles[type] || 'textbox';
        },

        computeAccessibleName: function(el) {
            const ariaLabel = el.getAttribute('aria-label');
            if (ariaLabel) return ariaLabel;
            
            const tag = el.tagName.toLowerCase();
            if (tag === 'input' || tag === 'textarea') {
                const placeholder = el.getAttribute('placeholder');
                if (placeholder) return placeholder;
            }
            if (tag === 'img') {
                const alt = el.getAttribute('alt');
                if (alt) return alt;
            }
            
            const textTags = ['a', 'button', 'label', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'option', 'summary'];
            if (textTags.includes(tag)) {
                const text = (el.textContent || '').trim();
                if (text && text.length < 200) return text;
            }
            
            const title = el.getAttribute('title');
            if (title) return title;
            
            const text = (el.textContent || '').trim();
            if (text && text.length < 50) return text;
            
            return null;
        },

        computeState: function(el) {
            const state = {};
            const attrs = ['disabled', 'readonly', 'required', 'checked', 'selected', 'hidden', 'open'];
            attrs.forEach(attr => {
                if (el.hasAttribute(attr)) state[attr === 'open' ? 'expanded' : attr] = true;
            });
            
            const ariaStates = {
                'aria-disabled': 'disabled',
                'aria-hidden': 'hidden',
                'aria-expanded': 'expanded',
                'aria-selected': 'selected',
                'aria-checked': 'checked',
                'aria-pressed': 'pressed',
                'aria-invalid': 'invalid',
                'aria-busy': 'busy'
            };
            
            Object.entries(ariaStates).forEach(([ariaAttr, stateKey]) => {
                const val = el.getAttribute(ariaAttr);
                if (val === 'true') state[stateKey] = true;
                if (val === 'false' && state[stateKey]) delete state[stateKey];
            });
            
            return state;
        },

        computeValue: function(el) {
            const tag = el.tagName.toLowerCase();
            if (['input', 'textarea'].includes(tag)) {
                const type = el.getAttribute('type');
                if (type === 'password') return null;
                const val = el.value || el.getAttribute('value');
                return val && val.length < 100 ? val : null;
            }
            if (['progress', 'meter'].includes(tag)) {
                return el.getAttribute('value');
            }
            return null;
        },

        getAccessibilityTree: function() {
            const self = this;
            const actionableRoles = new Set(['button', 'link', 'textbox', 'checkbox', 'radio', 'combobox', 'listbox', 'menuitem', 'option', 'slider', 'spinbutton', 'switch', 'tab', 'searchbox']);

            function buildA11yNode(el, depth) {
                if (depth > 15) return null;
                
                const tag = el.tagName.toLowerCase();
                const role = self.computeRole(el);
                const name = self.computeAccessibleName(el);
                const state = self.computeState(el);
                
                // Build children
                const children = [];
                Array.from(el.children).forEach(child => {
                    const childNode = buildA11yNode(child, depth + 1);
                    if (childNode) children.push(childNode);
                });
                
                // Shadow DOM
                if (el.shadowRoot) {
                    Array.from(el.shadowRoot.children).forEach(child => {
                        const childNode = buildA11yNode(child, depth + 1);
                        if (childNode) children.push(childNode);
                    });
                }
                
                // Skip non-semantic containers
                if (!role && !name && Object.keys(state).length === 0) {
                    if (children.length === 1) return children[0];
                    if (children.length > 1) {
                        return {
                            role: 'group',
                            selector: self.getSelector(el),
                            children: children
                        };
                    }
                    return null;
                }
                
                return {
                    role: role || 'generic',
                    name: name,
                    state: Object.keys(state).length > 0 ? state : undefined,
                    value: self.computeValue(el),
                    selector: self.getSelector(el),
                    children: children.length > 0 ? children : undefined,
                    description: el.getAttribute('title')
                };
            }

            const tree = buildA11yNode(document.body, 0);
            this.sendToKotlin('AccessibilityTreeUpdated', { root: tree });
            return tree;
        },

        // Get only actionable elements from accessibility tree
        getActionableElements: function() {
            const self = this;
            const actionableRoles = new Set(['button', 'link', 'textbox', 'checkbox', 'radio', 'combobox', 'listbox', 'menuitem', 'option', 'slider', 'spinbutton', 'switch', 'tab', 'searchbox']);
            const elements = [];

            function findActionable(el) {
                const role = self.computeRole(el);
                if (role && actionableRoles.has(role)) {
                    elements.push({
                        role: role,
                        name: self.computeAccessibleName(el),
                        state: self.computeState(el),
                        value: self.computeValue(el),
                        selector: self.getSelector(el)
                    });
                }
                
                Array.from(el.children).forEach(findActionable);
                if (el.shadowRoot) {
                    Array.from(el.shadowRoot.children).forEach(findActionable);
                }
            }

            findActionable(document.body);
            this.sendToKotlin('ActionableElementsUpdated', { elements: elements });
            return elements;
        },

        // Send message to Kotlin
        sendToKotlin: function(type, data) {
            console.log('[WebEditBridge] sendToKotlin called:', type);
            console.log('[WebEditBridge] Data preview:', JSON.stringify(data).substring(0, 100));
            console.log('[WebEditBridge] kmpJsBridge available:', typeof window.kmpJsBridge);
            
            if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                console.log('[WebEditBridge] Calling kmpJsBridge.callNative...');
                try {
                    const messageObj = { type: type, data: data };
                    const message = JSON.stringify(messageObj);
                    console.log('[WebEditBridge] Sending message length:', message.length);
                    window.kmpJsBridge.callNative(
                        'webEditMessage',
                        message,
                        function(result) {
                            console.log('[WebEditBridge] ✓ Kotlin callback received:', result);
                        }
                    );
                    console.log('[WebEditBridge] ✓ callNative invoked successfully');
                } catch (e) {
                    console.error('[WebEditBridge] ✗ Error calling native:', e);
                    console.error('[WebEditBridge] Error stack:', e.stack);
                }
            } else {
                console.error('[WebEditBridge] ✗ kmpJsBridge not available!');
                console.error('[WebEditBridge] window keys:', Object.keys(window).filter(k => k.includes('bridge') || k.includes('Bridge')));
            }
        }
    };
    
    console.log('[WebEditBridge] Bridge object created');
    
    // Send initial PageLoaded message
    console.log('[WebEditBridge] Sending PageLoaded message...');
    window.webEditBridge.sendToKotlin('PageLoaded', {
        url: window.location.href,
        title: document.title
    });

    // ========== Event Handlers for Inspect Mode ==========
    let lastHoverTime = 0;
    const HOVER_THROTTLE = 50; // ms

    document.addEventListener('mousemove', function(e) {
        if (!window.webEditBridge.inspectMode) return;

        const now = Date.now();
        if (now - lastHoverTime < HOVER_THROTTLE) return;
        lastHoverTime = now;

        const el = getElementAtPoint(e.clientX, e.clientY);
        if (!el || el === window.webEditBridge.highlightedElement) return;

        window.webEditBridge.highlightedElement = el;
        window.webEditBridge.updateHighlightBox(el, hoverBox);
    }, true);

    document.addEventListener('click', function(e) {
        if (!window.webEditBridge.inspectMode) return;
        e.preventDefault();
        e.stopPropagation();

        const el = getElementAtPoint(e.clientX, e.clientY);
        if (!el) return;

        window.webEditBridge.selectedElement = el;
        window.webEditBridge.updateHighlightBox(el, selectedBox);

        const rect = el.getBoundingClientRect();
        window.webEditBridge.sendToKotlin('ElementSelected', {
            element: {
                id: (typeof crypto !== 'undefined' && crypto.randomUUID) ? crypto.randomUUID() : (Date.now().toString(36) + Math.random().toString(36).slice(2, 11)),
                tagName: el.tagName.toLowerCase(),
                selector: window.webEditBridge.getSelector(el),
                textContent: (el.textContent || '').trim().substring(0, 100),
                attributes: window.webEditBridge.getElementAttributes(el),
                boundingBox: { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
                isShadowHost: !!el.shadowRoot
            }
        });
    }, true);

    // Update highlight positions on scroll/resize
    window.addEventListener('scroll', function() {
        if (window.webEditBridge.highlightedElement) {
            window.webEditBridge.updateHighlightBox(window.webEditBridge.highlightedElement, hoverBox);
        }
        if (window.webEditBridge.selectedElement) {
            window.webEditBridge.updateHighlightBox(window.webEditBridge.selectedElement, selectedBox);
        }
    }, true);

    window.addEventListener('resize', function() {
        if (window.webEditBridge.highlightedElement) {
            window.webEditBridge.updateHighlightBox(window.webEditBridge.highlightedElement, hoverBox);
        }
        if (window.webEditBridge.selectedElement) {
            window.webEditBridge.updateHighlightBox(window.webEditBridge.selectedElement, selectedBox);
        }
    });

    // Notify page loaded
    window.webEditBridge.sendToKotlin('PageLoaded', {
        url: window.location.href,
        title: document.title
    });

    // Monitor for JavaScript errors
    window.addEventListener('error', function(event) {
        const message = event.message || 'Unknown error';
        const source = event.filename || '';
        const lineno = event.lineno || 0;
        const errorMsg = `JavaScript Error: ${'$'}{message} at ${'$'}{source}:${'$'}{lineno}`;
        console.error('[WebEditBridge]', errorMsg);
        window.webEditBridge.sendToKotlin('Error', { message: errorMsg });
    }, true);

    // Monitor for unhandled promise rejections
    window.addEventListener('unhandledrejection', function(event) {
        const reason = event.reason || 'Unknown rejection';
        const errorMsg = `Unhandled Promise Rejection: ${'$'}{reason}`;
        console.error('[WebEditBridge]', errorMsg);
        window.webEditBridge.sendToKotlin('Error', { message: errorMsg });
    });

    console.log('[WebEditBridge] Initialized with Shadow DOM support');
})();
""".trimIndent()

