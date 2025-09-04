class Dashboard {
    constructor() {
        this.loadAll();
        this.setupAutoRefresh();
        this.setupEventDelegation();
    }
    async loadContent(url, targetId) {
        try {
            const response = await fetch(url);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const content = await response.text();
            document.getElementById(targetId).innerHTML = content;
            
            // If we loaded the historical graph, initialize it
            if (targetId === 'historical-graph' && window.initHistoricalChart) {
                window.initHistoricalChart();
                // Initialize date controls after a short delay to ensure DOM is ready
                setTimeout(() => {
                    if (window.initDateRangeControls) {
                        window.initDateRangeControls();
                    }
                }, 100);
            }
        } catch (error) {
            document.getElementById(targetId).innerHTML = '<div class="error">Failed to load data</div>';
            console.error(`Failed to load ${url}:`, error);
        }
    }
    
    loadAll() {
        this.loadContent('/dashboard/device-status', 'device-status');
        this.loadContent('/dashboard/current-conditions', 'current-conditions');
        this.loadContent('/dashboard/signal-strength', 'signal-strength');
        this.loadContent('/dashboard/controls', 'controls');
        this.loadContent('/dashboard/system-info', 'system-info');
        this.loadContent('/dashboard/historical-graph', 'historical-graph');
    }
    
    setupAutoRefresh() {
        // Device status - every 30s
        setInterval(() => this.loadContent('/dashboard/device-status', 'device-status'), 30000);
        
        // Current conditions - every 15s
        setInterval(() => this.loadContent('/dashboard/current-conditions', 'current-conditions'), 15000);
        
        // Signal strength - every 60s
        setInterval(() => this.loadContent('/dashboard/signal-strength', 'signal-strength'), 60000);
        
        // System info - every 120s
        setInterval(() => this.loadContent('/dashboard/system-info', 'system-info'), 120000);
    }
    
    setupEventDelegation() {
        document.addEventListener('click', async (e) => {
            if (e.target.hasAttribute('data-action')) {
                e.preventDefault();
                const action = e.target.getAttribute('data-action');
                const target = e.target.getAttribute('data-target') || 'controls';
                
                try {
                    e.target.textContent = 'Loading...';
                    const response = await fetch(action);
                    if (!response.ok) throw new Error(`HTTP ${response.status}`);
                    
                    // Reload the controls section
                    await this.loadContent('/dashboard/controls', target);
                } catch (error) {
                    console.error('Action failed:', error);
                    e.target.textContent = 'Error';
                    setTimeout(() => this.loadContent('/dashboard/controls', target), 1000);
                }
            }
        });
    }
    
    // Function to refresh all dashboard data including historical graph
    refreshAll() {
        this.loadAll();
        if (window.refreshHistoricalChart) {
            window.refreshHistoricalChart();
        }
    }
}

// Historical Chart functionality
window.initHistoricalChart = function() {
    const chartContainer = document.getElementById('historicalChart');
    if (!chartContainer) return;
    
    const ctx = chartContainer.getContext('2d');
    let chart;

    function initChart() {
        // Destroy existing chart if it exists
        if (chart) {
            chart.destroy();
        }
        
        chart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: [],
                datasets: [
                    {
                        label: 'Lux',
                        data: [],
                        borderColor: '#4CAF50',
                        backgroundColor: 'rgba(76, 175, 80, 0.1)',
                        tension: 0.1,
                        fill: false
                    },
                    {
                        label: 'Visible Light',
                        data: [],
                        borderColor: '#2196F3',
                        backgroundColor: 'rgba(33, 150, 243, 0.1)',
                        tension: 0.1,
                        fill: false
                    },
                    {
                        label: 'Infrared',
                        data: [],
                        borderColor: '#FF9800',
                        backgroundColor: 'rgba(255, 152, 0, 0.1)',
                        tension: 0.1,
                        fill: false
                    },
                    {
                        label: 'Full Spectrum',
                        data: [],
                        borderColor: '#9C27B0',
                        backgroundColor: 'rgba(156, 39, 176, 0.1)',
                        tension: 0.1,
                        fill: false
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'top',
                        labels: {
                            color: '#f8f9fa',
                            usePointStyle: true
                        }
                    },
                    title: {
                        display: false
                    }
                },
                scales: {
                    x: {
                        grid: {
                            color: '#495057'
                        },
                        ticks: {
                            color: '#adb5bd',
                            maxTicksLimit: 10
                        }
                    },
                    y: {
                        beginAtZero: true,
                        grid: {
                            color: '#495057'
                        },
                        ticks: {
                            color: '#adb5bd'
                        }
                    }
                },
                interaction: {
                    intersect: false,
                    mode: 'index'
                }
            }
        });
    }

    function formatTimeLabel(dateString) {
        const date = new Date(dateString);
        return date.toLocaleTimeString('en-US', { 
            hour: '2-digit', 
            minute: '2-digit',
            month: 'short',
            day: 'numeric'
        });
    }

    function loadGraphData(customStartDate = null, customEndDate = null) {
        const endDate = customEndDate || new Date();
        const startDate = customStartDate || new Date(endDate.getTime() - (24 * 60 * 60 * 1000)); // Last 24 hours
        
        const startDateStr = startDate.toISOString();
        const endDateStr = endDate.toISOString();
        
        fetch(`/api/v1/graph?start=${encodeURIComponent(startDateStr)}&end=${encodeURIComponent(endDateStr)}`)
            .then(response => {
                if (!response.ok) throw new Error(`HTTP ${response.status}`);
                return response.json();
            })
            .then(data => {
                if (!data || data.length === 0) {
                    // Show empty state
                    chart.data.labels = ['No data available'];
                    chart.data.datasets.forEach(dataset => {
                        dataset.data = [0];
                    });
                    chart.update();
                    return;
                }

                // Process data for chart - limit to reasonable number of points
                const maxPoints = 50;
                const step = Math.max(1, Math.floor(data.length / maxPoints));
                const sampledData = data.filter((_, index) => index % step === 0);

                const labels = sampledData.map(item => formatTimeLabel(item.created_at));
                const luxData = sampledData.map(item => item.lux || 0);
                const visibleData = sampledData.map(item => item.visible || 0);
                const infraredData = sampledData.map(item => item.infrared || 0);
                const fullSpectrumData = sampledData.map(item => item.full_spectrum || 0);

                chart.data.labels = labels;
                chart.data.datasets[0].data = luxData;
                chart.data.datasets[1].data = visibleData;
                chart.data.datasets[2].data = infraredData;
                chart.data.datasets[3].data = fullSpectrumData;
                
                chart.update('none'); // No animation for better performance
            })
            .catch(error => {
                console.error('Failed to load graph data:', error);
                // Show error state
                chart.data.labels = ['Error loading data'];
                chart.data.datasets.forEach(dataset => {
                    dataset.data = [0];
                });
                chart.update();
            });
    }

    // Initialize chart and load data
    initChart();
    loadGraphData();

    // Set up periodic refresh (every 60 seconds) - but clear any existing interval first
    if (window.chartRefreshInterval) {
        clearInterval(window.chartRefreshInterval);
    }
    window.chartRefreshInterval = setInterval(loadGraphData, 60000);
    
    // Expose the loadGraphData function globally so it can be called from refresh button
    window.refreshHistoricalChart = loadGraphData;
    window.refreshHistoricalChartWithRange = function(startDate, endDate) {
        loadGraphData(startDate, endDate);
    };
};

// Date range control functions for historical chart
window.initDateRangeControls = function() {
    const now = new Date();
    const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
    
    // Format dates for datetime-local input
    const formatDateTime = (date) => {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day}T${hours}:${minutes}`;
    };
    
    // Set default values (last 24 hours)
    const startDateInput = document.getElementById('startDate');
    const endDateInput = document.getElementById('endDate');
    
    if (startDateInput && endDateInput) {
        startDateInput.value = formatDateTime(yesterday);
        endDateInput.value = formatDateTime(now);
    }
};

window.updateGraphDateRange = function() {
    if (typeof window.refreshHistoricalChartWithRange === 'function') {
        const startDateInput = document.getElementById('startDate');
        const endDateInput = document.getElementById('endDate');
        
        if (startDateInput && endDateInput) {
            const startDate = startDateInput.value;
            const endDate = endDateInput.value;
            
            if (startDate && endDate) {
                window.refreshHistoricalChartWithRange(new Date(startDate), new Date(endDate));
            }
        }
    }
};

window.setQuickRange = function(range) {
    const now = new Date();
    let startDate;
    
    switch(range) {
        case '1h':
            startDate = new Date(now.getTime() - (1 * 60 * 60 * 1000));
            break;
        case '6h':
            startDate = new Date(now.getTime() - (6 * 60 * 60 * 1000));
            break;
        case '24h':
            startDate = new Date(now.getTime() - (24 * 60 * 60 * 1000));
            break;
        case '7d':
            startDate = new Date(now.getTime() - (7 * 24 * 60 * 60 * 1000));
            break;
        default:
            startDate = new Date(now.getTime() - (24 * 60 * 60 * 1000));
    }
    
    const formatDateTime = (date) => {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day}T${hours}:${minutes}`;
    };
    
    const startDateInput = document.getElementById('startDate');
    const endDateInput = document.getElementById('endDate');
    
    if (startDateInput && endDateInput) {
        startDateInput.value = formatDateTime(startDate);
        endDateInput.value = formatDateTime(now);
        
        window.updateGraphDateRange();
    }
};