import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { SettingsService } from '../../core/services/settings.service';
import { NotificationService } from '../../core/services/notification.service';
import { CompanySettings } from '../../core/models/settings.model';

@Component({
  selector: 'app-settings-view',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule],
  templateUrl: './settings-view.component.html',
  styleUrl: './settings-view.component.scss'
})
export class SettingsViewComponent implements OnInit {
  settings: CompanySettings = this.defaultSettings();

  saving = false;
  saveMessage = '';

  constructor(private settingsService: SettingsService, private notify: NotificationService) {}

  ngOnInit(): void {
    this.settingsService.get().subscribe({
      next: (res) => {
        if (res.data) {
          this.settings = res.data;
        }
      }
    });
  }

  save(): void {
    this.saving = true;
    this.saveMessage = '';

    this.settingsService.update(this.settings).subscribe({
      next: (res) => {
        this.saving = false;
        this.settings = res.data;
        this.saveMessage = 'Paramètres enregistrés avec succès';
        this.notify.success('Paramètres enregistrés avec succès');
        setTimeout(() => this.saveMessage = '', 3000);
      },
      error: () => {
        this.saving = false;
        this.saveMessage = 'Erreur lors de l\'enregistrement';
        this.notify.error('Erreur lors de l\'enregistrement des paramètres');
      }
    });
  }

  toggleDarkMode(): void {
    this.settings.theme = this.settings.theme === 'dark' ? 'light' : 'dark';
  }

  onLogoUpload(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];
    if (file.size > 2 * 1024 * 1024) {
      alert('Logo trop volumineux (max 2MB)');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement('canvas');
        const maxSize = 400;
        let w = img.width;
        let h = img.height;
        if (w > maxSize || h > maxSize) {
          if (w > h) { h = Math.round(h * maxSize / w); w = maxSize; }
          else { w = Math.round(w * maxSize / h); h = maxSize; }
        }
        canvas.width = w;
        canvas.height = h;
        canvas.getContext('2d')!.drawImage(img, 0, 0, w, h);
        const format = file.type === 'image/png' ? 'image/png' : 'image/jpeg';
        const quality = format === 'image/png' ? undefined : 0.6;
        this.settings.companyLogo = canvas.toDataURL(format, quality);
      };
      img.src = reader.result as string;
    };
    reader.readAsDataURL(file);
  }

  private defaultSettings(): CompanySettings {
    return {
      companyName: '',
      companySector: '',
      companyAddress: '',
      companyEmail: '',
      companyPhone: '',
      companyLogo: null,
      lateGraceMinutes: 15,
      overtimeRate: 1.5,
      cnssRate: 9.18,
      cnssEmployerRate: 16.57,
      cnssCeiling: 5173.085,
      assuranceRate: 0.50,
      irTranche1: 5000,
      irRate1: 0,
      irTranche2: 10000,
      irRate2: 15,
      irTranche3: 20000,
      irRate3: 25,
      irTranche4: 30000,
      irRate4: 30,
      irTranche5: 40000,
      irRate5: 33,
      irTranche6: 50000,
      irRate6: 36,
      irTranche7: 70000,
      irRate7: 38,
      irTranche8: 999999,
      irRate8: 40,
      irAbatement: 0,
      irFraisProPercent: 10,
      irFraisProCap: 2000,
      irCssRate: 0.5,
      language: 'fr',
      theme: 'light'
    };
  }
}
