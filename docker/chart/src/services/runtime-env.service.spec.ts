import { TestBed } from '@angular/core/testing';

import { RuntimeEnvService } from './runtime-env.service';

describe('RuntimeEnvService', () => {
  beforeEach(() => TestBed.configureTestingModule({}));

  it('should be created', () => {
    const service: RuntimeEnvService = TestBed.get(RuntimeEnvService);
    expect(service).toBeTruthy();
  });
});
