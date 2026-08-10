import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { toArray } from 'rxjs/operators';
import { ConversationService } from './conversation.service';

describe('ConversationService', () => {
  let service: ConversationService;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ConversationService);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list() fetches the tenant conversations', () => {
    service.list(1).subscribe();

    const req = httpMock.expectOne('/api/tenants/1/conversations');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('create() posts a new conversation with title and icon', () => {
    service.create(1, 'Base de artigos de RH', 'BOOK_OPEN').subscribe();

    const req = httpMock.expectOne('/api/tenants/1/conversations');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ title: 'Base de artigos de RH', icon: 'BOOK_OPEN' });
    req.flush({ id: 5, title: 'Base de artigos de RH', icon: 'BOOK_OPEN' });
  });

  it('create() omits icon when not passed', () => {
    service.create(1, 'Base de artigos de RH').subscribe();

    const req = httpMock.expectOne('/api/tenants/1/conversations');
    expect(req.request.body).toEqual({ title: 'Base de artigos de RH', icon: undefined });
    req.flush({ id: 5, title: 'Base de artigos de RH', icon: null });
  });

  it('rename() PUTs the new title/icon and returns the updated summary', async () => {
    const result = service.rename(1, 5, 'Novo nome', 'ROCKET');
    const promise = firstValueFrom(result);

    const req = httpMock.expectOne('/api/tenants/1/conversations/5');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ title: 'Novo nome', icon: 'ROCKET' });
    req.flush({ id: 5, title: 'Novo nome', icon: 'ROCKET' });

    expect(await promise).toEqual({ id: 5, title: 'Novo nome', icon: 'ROCKET' });
  });

  it("rename() surfaces a 400 (blank title/invalid icon) distinctly from a 404 (not the caller's own)", async () => {
    const badRequest = firstValueFrom(service.rename(1, 5, '', undefined));
    httpMock
      .expectOne('/api/tenants/1/conversations/5')
      .flush({ message: 'blank title' }, { status: 400, statusText: 'Bad Request' });
    await expect(badRequest).rejects.toMatchObject({ status: 400 });

    const notOwned = firstValueFrom(service.rename(1, 6, 'Nome', undefined));
    httpMock
      .expectOne('/api/tenants/1/conversations/6')
      .flush({ message: 'not found' }, { status: 404, statusText: 'Not Found' });
    await expect(notOwned).rejects.toMatchObject({ status: 404 });
  });

  it('getDetail() fetches a conversation with its messages', () => {
    service.getDetail(1, 5).subscribe();

    const req = httpMock.expectOne('/api/tenants/1/conversations/5');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 5, title: null, messages: [] });
  });

  describe('sendMessage()', () => {
    function fakeFetchResponse(chunks: string[], ok = true, status = 200): Response {
      const encoder = new TextEncoder();
      const body = new ReadableStream<Uint8Array>({
        start(controller) {
          for (const chunk of chunks) {
            controller.enqueue(encoder.encode(chunk));
          }
          controller.close();
        },
      });
      return { ok, status, body } as unknown as Response;
    }

    afterEach(() => {
      vi.restoreAllMocks();
    });

    it('parses message and done events from the stream', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(
        fakeFetchResponse(['event:message\ndata:Hello\n\n', 'event:done\ndata:\n\n']),
      );

      const events = await firstValueFrom(service.sendMessage(1, 5, 'question').pipe(toArray()));

      expect(events).toEqual([{ type: 'message', data: 'Hello' }, { type: 'done' }]);
    });

    it('parses an error event from the stream', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(
        fakeFetchResponse(['event:error\ndata:The assistant is unavailable.\n\n']),
      );

      const events = await firstValueFrom(service.sendMessage(1, 5, 'question').pipe(toArray()));

      expect(events).toEqual([{ type: 'error', data: 'The assistant is unavailable.' }]);
    });

    it('emits permission-denied on a 403 response instead of parsing the body as SSE', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(fakeFetchResponse([], false, 403));

      const events = await firstValueFrom(service.sendMessage(1, 5, 'question').pipe(toArray()));

      expect(events).toEqual([{ type: 'permission-denied' }]);
    });

    it('bug fix (2026-08-10): a 401 (expired session) mid-stream redirects to /login instead of showing a generic "assistant unavailable" error', async () => {
      const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(fakeFetchResponse([], false, 401));

      const events = await firstValueFrom(service.sendMessage(1, 5, 'question').pipe(toArray()));

      expect(navigateSpy).toHaveBeenCalledWith('/login');
      expect(events).not.toEqual([{ type: 'error', data: 'The assistant is unavailable.' }]);
    });

    it('emits an error event on a non-403 failure response', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(fakeFetchResponse([], false, 500));

      const events = await firstValueFrom(service.sendMessage(1, 5, 'question').pipe(toArray()));

      expect(events).toEqual([{ type: 'error', data: 'The assistant is unavailable.' }]);
    });

    it('sends the content as a JSON POST body with credentials', () => {
      const fetchSpy = vi
        .spyOn(globalThis, 'fetch')
        .mockResolvedValue(fakeFetchResponse(['event:done\ndata:\n\n']));

      service.sendMessage(1, 5, 'question').subscribe();

      expect(fetchSpy).toHaveBeenCalledWith(
        '/api/tenants/1/conversations/5/messages',
        expect.objectContaining({
          method: 'POST',
          credentials: 'include',
          body: JSON.stringify({ content: 'question' }),
        }),
      );
    });
  });
});
