import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type ArticleStatus = 'PROCESSING' | 'READY' | 'FAILED';

export interface ArticleSummary {
  id: number;
  title: string;
  status: ArticleStatus;
}

export interface ArticleDetail extends ArticleSummary {
  text: string | null;
  failureReason: string | null;
  originalFileUrl: string;
}

@Injectable({ providedIn: 'root' })
export class ArticleService {
  private readonly http = inject(HttpClient);

  list(tenantId: number): Observable<ArticleSummary[]> {
    return this.http.get<ArticleSummary[]>(`/api/tenants/${tenantId}/articles`);
  }

  upload(tenantId: number, title: string, file: File): Observable<ArticleSummary> {
    const formData = new FormData();
    formData.set('title', title);
    formData.set('file', file);

    return this.http.post<ArticleSummary>(`/api/tenants/${tenantId}/articles`, formData);
  }

  getDetail(tenantId: number, articleId: number): Observable<ArticleDetail> {
    return this.http.get<ArticleDetail>(`/api/tenants/${tenantId}/articles/${articleId}`);
  }

  update(
    tenantId: number,
    articleId: number,
    title: string,
    text: string,
  ): Observable<ArticleDetail> {
    return this.http.put<ArticleDetail>(`/api/tenants/${tenantId}/articles/${articleId}`, {
      title,
      text,
    });
  }

  remove(tenantId: number, articleId: number): Observable<void> {
    return this.http.delete<void>(`/api/tenants/${tenantId}/articles/${articleId}`);
  }
}
