import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ChatDirectoryService } from './chat-directory.service';
import { ChatDiscoverableGroupDto } from './chat.model';

describe('ChatDirectoryService', () => {
  let service: ChatDirectoryService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ChatDirectoryService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('fetchDiscoverableGroups calls GET /api/chat/discoverable-groups?page=0&size=200 with no tenantId param and unwraps content', () => {
    service.fetchDiscoverableGroups();

    const req = httpMock.expectOne(
      (r) => r.url === '/api/chat/discoverable-groups' && r.method === 'GET',
    );
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('200');
    expect(req.request.params.has('tenantId')).toBe(false);

    const groups: ChatDiscoverableGroupDto[] = [
      { id: 1, title: 'Grupo A', tenantId: 1, visibility: 'PUBLIC', participantCount: 3 },
    ];
    req.flush({ content: groups, page: 0, size: 200, totalElements: 1, totalPages: 1 });

    expect(service.discoverableGroups()).toEqual(groups);
  });

  it('applies no client-side visibility/membership filtering — output equals fetched content exactly', () => {
    service.fetchDiscoverableGroups();

    const groups: ChatDiscoverableGroupDto[] = [
      { id: 1, title: 'Grupo Público', tenantId: 1, visibility: 'PUBLIC', participantCount: 3 },
      {
        id: 2,
        title: 'Grupo Solicitação',
        tenantId: 1,
        visibility: 'REQUEST_TO_JOIN',
        participantCount: 5,
      },
    ];
    httpMock
      .expectOne((r) => r.url === '/api/chat/discoverable-groups')
      .flush({ content: groups, page: 0, size: 200, totalElements: 2, totalPages: 1 });

    expect(service.discoverableGroups()).toEqual(groups);
    expect(service.discoverableGroups().length).toBe(2);
  });
});
