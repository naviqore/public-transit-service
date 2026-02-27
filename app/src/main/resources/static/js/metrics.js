/**
 * MetricsExplorer Class
 * Hierarchical tree management with search-driven auto-expansion
 */
class MetricsExplorer {
    constructor() {
        this.apiBase = window.ACTUATOR_URL || '/actuator/metrics';
        this.allMetrics = [];

        this.elements = {
            list: document.getElementById("metricsList"),
            detail: document.getElementById("metricDetail"),
            search: document.getElementById("searchInput"),
            dashboard: document.getElementById("dashboard"),
            backBtn: document.getElementById("backToListBtn")
        };

        this.init();
    }

    async init() {
        this.bindEvents();

        const response = await fetch(this.apiBase).catch(() => null);
        if (!response?.ok) {
            this.elements.list.innerHTML = `<div class="error">Registry unreachable.</div>`;
            return;
        }

        const data = await response.json();
        this.allMetrics = data['names'] || [];
        this.renderTree();
    }

    bindEvents() {
        // Toggle Accordion Groups
        this.elements.list.addEventListener('click', (e) => {
            const label = e.target.closest('.group-label');
            if (label) {
                const node = label.parentElement;
                this.toggleGroup(node);
                return;
            }

            const btn = e.target.closest('.metric-btn');
            if (btn) this.loadMetric(btn.dataset.metric);
        });

        this.elements.search.addEventListener('input', () => this.renderTree());
        this.elements.backBtn.addEventListener('click', () => this.elements.dashboard.classList.remove('is-viewing'));
    }

    /**
     * Toggles a single node. Ensures sibling branches at the same level close.
     */
    toggleGroup(node) {
        const isExpanded = node.classList.contains('expanded');

        // standard accordion: close siblings
        const siblings = node.parentElement.querySelectorAll(':scope > .group-node');
        siblings.forEach(s => s.classList.remove('expanded'));

        if (!isExpanded) node.classList.add('expanded');
    }

    /**
     * Tree Construction Logic
     */
    renderTree() {
        const term = this.elements.search.value.toLowerCase();
        if (!this.allMetrics.length) return;

        const tree = {};
        this.allMetrics.forEach(name => {
            if (term && !name.toLowerCase().includes(term)) return;

            const parts = name.split('.');
            let current = tree;
            parts.forEach((part, i) => {
                if (i === parts.length - 1) {
                    current[part] = {_leaf: name};
                } else {
                    current[part] = current[part] || {};
                    current = current[part];
                }
            });
        });

        const html = this.generateTreeHTML(tree, term !== "");
        this.elements.list.innerHTML = html || `<div class="message">No metrics match your search.</div>`;
    }

    generateTreeHTML(obj, forceExpand = false) {
        return Object.keys(obj).sort().map(key => {
            const node = obj[key];
            if (node._leaf) {
                return `<button class="metric-btn" data-metric="${node._leaf}">${key}</button>`;
            } else {
                const expandedClass = forceExpand ? 'expanded' : '';
                return `
                    <div class="group-node ${expandedClass}">
                        <div class="group-label"><span>${key}</span></div>
                        <div class="group-content">${this.generateTreeHTML(node, forceExpand)}</div>
                    </div>
                `;
            }
        }).join('');
    }

    async loadMetric(name) {
        this.elements.dashboard.classList.add('is-viewing');
        this.elements.detail.innerHTML = `<div class="message">Fetching ${name}...</div>`;

        const response = await fetch(`${this.apiBase}/${name}`).catch(() => null);
        if (!response?.ok) {
            this.elements.detail.innerHTML = `<div class="error">Data point unavailable.</div>`;
            return;
        }

        const data = await response.json();
        this.renderDetails(data);
    }

    renderDetails(data) {
        const unit = data['baseUnit'] || 'count';
        this.elements.detail.innerHTML = `
            <h2 class="metric-title">${data['name']}</h2>
            <p class="metric-desc">${data['description'] || 'No additional metadata available.'}</p>
            <div class="badge" style="margin-bottom: 2rem">${unit}</div>
            <div class="measurements-grid">
                ${(data['measurements'] || []).map(m => `
                    <div class="stat-card">
                        <div class="stat-card-label">${m.statistic.replace(/_/g, ' ')}</div>
                        <div class="stat-card-value">${this.formatValue(m.value, unit)}</div>
                    </div>
                `).join('')}
            </div>
        `;
    }

    formatValue(value, unit) {
        if (typeof value !== 'number') return value;
        if (unit === 'bytes') {
            const i = value === 0 ? 0 : Math.floor(Math.log(value) / Math.log(1024));
            return (value / Math.pow(1024, i)).toFixed(2) + ' ' + ['B', 'KB', 'MB', 'GB', 'TB'][i];
        }
        if (unit === 'seconds') {
            return value < 1 ? (value * 1000).toFixed(2) + 'ms' : value.toFixed(2) + 's';
        }
        return Number.isInteger(value) ? value.toLocaleString() : value.toFixed(4);
    }
}

document.addEventListener("DOMContentLoaded", () => new MetricsExplorer());