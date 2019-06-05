import { Chart, ChartData, ChartDataSets, ChartOptions, ChartPoint } from 'chart.js';
import { BoardService, Patient } from 'src/services/board.service';

import { Component, OnDestroy, OnInit } from '@angular/core';

// Sets the default config for 'derivedLine' to be the same as the bubble defaults.
// We look for the defaults by doing Chart.defaults[chartType]
// It looks like a bug exists when the defaults don't exist
Chart.defaults.derivedLine = Chart.defaults.line;

// I think the recommend using Chart.controllers.bubble.extend({ extensions here });
const custom = Chart.controllers.line.extend({
  calculatePointY(value, index, datasetIndex) {
    const chart = this.chart;
    const meta = this.getMeta();
    const yScale = this.getScaleForId(meta.yAxisID);

    return yScale.getPixelForValue(-100000);
  },
});

// Stores the controller so that the chart initialization routine can look it up with
// Chart.controllers[type]
Chart.controllers.derivedLine = custom;

@Component({
  selector: 'app-charts',
  templateUrl: './charts.component.html',
  styleUrls: ['./charts.component.scss'],
})
export class ChartsComponent implements OnInit, OnDestroy {

  public pageControls = {
    partialPatientId: '',
  };

  public patients: Patient[] = undefined;
  public selectedPatientId: string = undefined;

  public intervalId: number = undefined;

  public processedIds = { aggregatedRecords: {}, alerts: {}, adwinAlerts: {}, latestAggregatedRecordTimestamp: 0, latestAlertTimestamp: 0 };
  public unitGroups: {
    unit: string;
    data: ChartData,
    options: ChartOptions,

    _minDataSets: ChartDataSets,
    _avgDataSets: ChartDataSets,
    _maxDataSets: ChartDataSets,
    _alertDataSets: ChartDataSets,

    _adwinAlerts: { from: Date, to: Date }[],
    _minY: number,
  }[] = [];

  public chartOptions: ChartOptions = {
    animation: {
      duration: 0,
    },
    scales: {
      xAxes: [
        {
          type: 'time',
          display: true,
          time: {
            unit: 'minute',
          },
        },
      ],
      yAxes: [
        {
          type: 'linear',
          ticks: {
            autoSkipPadding: 0,
          },
        },
      ],
    },
    tooltips: { mode: 'x', intersect: true, filter: tooltipItem => (tooltipItem.datasetIndex <= 3) },
    legend: { labels: { filter: legendItem => ['min', 'avg', 'max', 'alert'].includes(legendItem.text) } },
    devicePixelRatio: 2,
  };

  public defaultChartDataSets: ChartDataSets = {
    type: 'line',
    fill: false,
    lineTension: 0,
    borderWidth: 1,
    data: [],
  };

  constructor(private boardService: BoardService) {
  }

  ngOnInit(): void {
    this.loadPatients();
    this.intervalId = window.setInterval(
      () => {
        this.loadPatients();
        this.loadAggregatedRecords();
        this.loadAdwinAlerts();
      },
      10000);
  }

  ngOnDestroy(): void {
    clearInterval(this.intervalId);
  }

  public onKeyUpPartialPatientId() {
    if (this.pageControls.partialPatientId.length >= 4) {
      this.patients = undefined;
      this.boardService.getPatients(this.pageControls.partialPatientId).subscribe((patients) => {
        this.patients = patients;
      });
    }

    this.loadPatients();
  }

  public onClickPatientId(patientId: string) {
    this.selectedPatientId = patientId;

    // Reset
    this.processedIds = { aggregatedRecords: {}, alerts: {}, adwinAlerts: {}, latestAggregatedRecordTimestamp: 0, latestAlertTimestamp: 0 };
    this.unitGroups = [];

    // Load
    this.loadAggregatedRecords();
    this.loadAlerts();
    this.loadAdwinAlerts();

    // Real-time
    this.boardService.setUpHubForPatient(patientId);
    this.boardService.newAlertObservable.subscribe(() => {
      this.loadAlerts();
    });
  }

  private loadPatients() {
    if (this.pageControls.partialPatientId.length < 4) {
      this.boardService.getRecentPatients().subscribe((patients) => {
        this.patients = patients;
      });
    }
  }

  private loadAggregatedRecords() {
    if (this.selectedPatientId === undefined) { return; }

    this.boardService
      .getPatientAggregatedRecords(this.selectedPatientId, this.processedIds.latestAggregatedRecordTimestamp === 0 ? undefined : this.processedIds.latestAggregatedRecordTimestamp)
      .subscribe((aggregatedRecords) => {
        aggregatedRecords
          .filter(r => Object.keys(this.processedIds.aggregatedRecords).includes(`${r.unit},${r.timestamp}`) === false)
          .forEach((r) => {
            this.processedIds.aggregatedRecords[`${r.unit},${r.timestamp}`] = true;
            this.processedIds.latestAggregatedRecordTimestamp = Math.max(this.processedIds.latestAggregatedRecordTimestamp, r.timestamp);

            const unitGroup = this.getUnitGroup(r.unit.split('/').join(' / '));

            (unitGroup._minDataSets.data as ChartPoint[]).push({ x: r.timestamp, y: r.min_value });
            (unitGroup._avgDataSets.data as ChartPoint[]).push({ x: r.timestamp, y: r.avg_value });
            (unitGroup._maxDataSets.data as ChartPoint[]).push({ x: r.timestamp, y: r.max_value });

            if (r.min_value < unitGroup._minY) {
              unitGroup._minY = r.min_value;
            }
          });

        this.redrawCharts();
      });
  }

  private loadAlerts() {
    if (this.selectedPatientId === undefined) { return; }

    this.boardService
      .getPatientAlerts(this.selectedPatientId, this.processedIds.latestAlertTimestamp === 0 ? undefined : this.processedIds.latestAlertTimestamp)
      .subscribe((alerts) => {
        alerts
          .filter(r => Object.keys(this.processedIds.alerts).includes(`${r.unit},${r.timestamp}`) === false)
          .forEach((r) => {
            this.processedIds.alerts[`${r.unit},${r.timestamp}`] = true;
            this.processedIds.latestAlertTimestamp = Math.max(this.processedIds.latestAlertTimestamp, r.timestamp);

            const unitGroup = this.getUnitGroup(r.unit.split('/').join(' / '));

            (unitGroup._alertDataSets.data as ChartPoint[]).push({ x: r.timestamp, y: r.value, r: 3 });
          });

        this.redrawCharts();
      });
  }

  private loadAdwinAlerts() {
    if (this.selectedPatientId === undefined) { return; }

    this.boardService
      .getPatientAdwinAlerts(this.selectedPatientId)
      .subscribe((adwinAlerts) => {
        adwinAlerts
          .filter(r => (r.id in this.processedIds.adwinAlerts) === false)
          .forEach((r) => {
            this.processedIds.adwinAlerts[r.id] = true;

            const unitGroup = this.getUnitGroup(r.unit.split('/').join(' / '));

            unitGroup._adwinAlerts.push({ from: new Date(r.from), to: new Date(r.to) });
          });

        this.redrawCharts();
      });
  }

  private getChartDataSets(label: string, borderColor: string, type?: string, xAxisID?: string) {
    const dataSets: ChartDataSets = JSON.parse(JSON.stringify(this.defaultChartDataSets));

    dataSets.label = label;
    dataSets.borderColor = borderColor;

    if (type !== undefined) {
      dataSets.type = type;
    }
    if (xAxisID !== undefined) {
      dataSets.xAxisID = xAxisID;
    }

    return dataSets;
  }

  private getUnitGroup(unit: string) {
    let unitGroup = this.unitGroups.find(ug => ug.unit === unit);
    if (unitGroup !== undefined) { return unitGroup; }

    // Default Unit Group
    unitGroup = {
      unit,
      data: [] as ChartData,
      options: this.chartOptions,

      _minDataSets: this.getChartDataSets('min', 'rgba(155,255,123,0.8)', 'line'),
      _avgDataSets: this.getChartDataSets('avg', '#4E7F3E', 'line'),
      _maxDataSets: this.getChartDataSets('max', 'rgba(155,255,123,0.8)', 'line'),
      _alertDataSets: this.getChartDataSets('alert', 'rgba(255,0,0,0.8)', 'bubble'),

      _adwinAlerts: [],
      _minY: 1000,
    };

    this.unitGroups.push(unitGroup);

    return unitGroup;
  }

  private redrawCharts() {
    this.unitGroups = this.unitGroups.sort((a, b) => a.unit < b.unit ? -1 : 1);

    for (let i = 0; i < this.unitGroups.length; i = i + 1) {
      const unitGroup = this.unitGroups[i];

      unitGroup._minDataSets.data = (unitGroup._minDataSets.data as ChartPoint[]).sort((a, b) => a.x < b.x ? -1 : 1);
      unitGroup._avgDataSets.data = (unitGroup._avgDataSets.data as ChartPoint[]).sort((a, b) => a.x < b.x ? -1 : 1);
      unitGroup._maxDataSets.data = (unitGroup._maxDataSets.data as ChartPoint[]).sort((a, b) => a.x < b.x ? -1 : 1);
      unitGroup._alertDataSets.data = (unitGroup._alertDataSets.data as ChartPoint[]).sort((a, b) => a.x < b.x ? -1 : 1);

      unitGroup.data = {
        datasets: [
          unitGroup._minDataSets,
          unitGroup._avgDataSets,
          unitGroup._maxDataSets,
          unitGroup._alertDataSets,
          ...
          unitGroup._adwinAlerts.map((ft) => {
            return {
              data: [
                { x: ft.from, y: unitGroup._minY },
                { x: ft.to, y: unitGroup._minY },
              ],
              fill: 'top',
              backgroundColor: 'rgba(255,105,48,0.1)',
              pointRadius: 0,
              label: JSON.stringify(ft),
              type: 'derivedLine',
            } as ChartDataSets;
          }),
        ],
      };
    }
  }
}
