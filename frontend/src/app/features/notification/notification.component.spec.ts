import { Router } from '@angular/router';

import { ApiService } from '@app/data-access/api.service';

import { NotificationComponent } from './notification.component';

describe('NotificationComponent', () => {
  it('normalizes the local number when building the recipient', () => {
    const api = {} as ApiService;
    const router = {} as Router;
    const component = new NotificationComponent(api, router);

    component.notifyCountryCode = '+1';
    component.notifyLocalNumber = ' (202) 555-0123 ';

    expect(component.computedRecipient).toBe('+12025550123');
  });
});
