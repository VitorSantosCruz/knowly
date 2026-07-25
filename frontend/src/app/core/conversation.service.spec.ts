import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { toArray } from 'rxjs/operators';
import { ConversationService } from './conversation.service';

describe('ConversationService', () => {
  let service: ConversationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ConversationService);
    httpMock = TestBed.inject(HttpTestingController);
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

  it('create() posts a new conversation', () => {
    service.create(1).subscribe();

    const req = httpMock.expectOne('/api/tenants/1/conversations');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 5, title: null });
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
