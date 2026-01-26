/**
 * Constellation Dashboard - File Browser Component
 *
 * Provides a tree view for browsing .cst files in the configured directory.
 */

class FileBrowser {
    constructor(containerId, onFileSelect) {
        this.container = document.getElementById(containerId);
        this.onFileSelect = onFileSelect;
        this.selectedFile = null;
        this.expandedFolders = new Set();
    }

    /**
     * Load the file tree from the API
     */
    async load() {
        try {
            const response = await fetch('/api/v1/files');
            if (!response.ok) {
                throw new Error(`Failed to load files: ${response.statusText}`);
            }
            const data = await response.json();
            this.render(data.files);
        } catch (error) {
            console.error('Error loading files:', error);
            this.container.innerHTML = `
                <div class="placeholder-text">
                    Failed to load files: ${error.message}
                </div>
            `;
        }
    }

    /**
     * Render the file tree
     */
    render(files) {
        if (!files || files.length === 0) {
            this.container.innerHTML = `
                <div class="placeholder-text">
                    No .cst files found
                </div>
            `;
            return;
        }

        this.container.innerHTML = '';
        this.renderNodes(files, this.container);
    }

    /**
     * Render file tree nodes recursively
     */
    renderNodes(nodes, parent) {
        nodes.forEach(node => {
            if (node.fileType === 'directory') {
                const folder = this.createFolderElement(node);
                parent.appendChild(folder);
            } else {
                const file = this.createFileElement(node);
                parent.appendChild(file);
            }
        });
    }

    /**
     * Create a folder element
     */
    createFolderElement(node) {
        const folder = document.createElement('div');
        folder.className = 'folder';
        if (!this.expandedFolders.has(node.path)) {
            folder.classList.add('collapsed');
        }

        const item = document.createElement('div');
        item.className = 'file-item';
        item.innerHTML = `
            <svg class="icon" viewBox="0 0 24 24">
                <path fill="currentColor" d="M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/>
            </svg>
            <span class="name">${this.escapeHtml(node.name)}</span>
            <svg class="chevron icon" viewBox="0 0 24 24" style="transform: ${this.expandedFolders.has(node.path) ? 'rotate(90deg)' : 'rotate(0)'}">
                <path fill="currentColor" d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/>
            </svg>
        `;

        item.addEventListener('click', (e) => {
            e.stopPropagation();
            this.toggleFolder(folder, node.path);
        });

        folder.appendChild(item);

        if (node.children && node.children.length > 0) {
            const contents = document.createElement('div');
            contents.className = 'folder-contents';
            this.renderNodes(node.children, contents);
            folder.appendChild(contents);
        }

        return folder;
    }

    /**
     * Create a file element
     */
    createFileElement(node) {
        const file = document.createElement('div');
        file.className = 'file';
        file.dataset.path = node.path;

        const item = document.createElement('div');
        item.className = 'file-item';
        if (this.selectedFile === node.path) {
            item.classList.add('selected');
        }

        item.innerHTML = `
            <svg class="icon" viewBox="0 0 24 24">
                <path fill="currentColor" d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm-1 7V3.5L18.5 9H13z"/>
            </svg>
            <span class="name">${this.escapeHtml(node.name)}</span>
        `;

        item.addEventListener('click', (e) => {
            e.stopPropagation();
            this.selectFile(node.path, item);
        });

        file.appendChild(item);
        return file;
    }

    /**
     * Toggle folder expansion
     */
    toggleFolder(folderElement, path) {
        const isCollapsed = folderElement.classList.contains('collapsed');
        folderElement.classList.toggle('collapsed');

        const chevron = folderElement.querySelector('.chevron');
        if (chevron) {
            chevron.style.transform = isCollapsed ? 'rotate(90deg)' : 'rotate(0)';
        }

        if (isCollapsed) {
            this.expandedFolders.add(path);
        } else {
            this.expandedFolders.delete(path);
        }
    }

    /**
     * Select a file
     */
    selectFile(path, itemElement) {
        // Remove previous selection
        const previousSelected = this.container.querySelector('.file-item.selected');
        if (previousSelected) {
            previousSelected.classList.remove('selected');
        }

        // Add new selection
        itemElement.classList.add('selected');
        this.selectedFile = path;

        // Notify callback
        if (this.onFileSelect) {
            this.onFileSelect(path);
        }
    }

    /**
     * Get the currently selected file path
     */
    getSelectedFile() {
        return this.selectedFile;
    }

    /**
     * Clear the selection
     */
    clearSelection() {
        const selected = this.container.querySelector('.file-item.selected');
        if (selected) {
            selected.classList.remove('selected');
        }
        this.selectedFile = null;
    }

    /**
     * Escape HTML to prevent XSS
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// Export for use in main.js
window.FileBrowser = FileBrowser;
