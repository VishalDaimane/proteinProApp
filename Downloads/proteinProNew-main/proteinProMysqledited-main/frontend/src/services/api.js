const API_BASE_URL = 'http://localhost:8080';

const getHeaders = (token, extraHeaders = {}) => {
  const headers = {
    'Content-Type': 'application/json',
    ...extraHeaders
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
};

const handleResponse = async (response) => {
  if (!response.ok) {
    let errorMessage = 'An error occurred';
    try {
      const errorData = await response.json();
      errorMessage = errorData.message || errorData.error || response.statusText;
    } catch (e) {
      errorMessage = `HTTP error ${response.status}`;
    }
    throw new Error(errorMessage);
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
};

export const authService = {
  login: async (email, password) => {
    const res = await fetch(`${API_BASE_URL}/api/auth/login`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ email, password })
    });
    return handleResponse(res);
  },

  register: async (firstName, lastName, email, password) => {
    const res = await fetch(`${API_BASE_URL}/api/profiles/register`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ firstName, lastName, email, password })
    });
    return handleResponse(res);
  },

  resetPassword: async (token, newPassword) => {
    const res = await fetch(`${API_BASE_URL}/api/auth/password-reset`, {
      method: 'POST',
      headers: getHeaders(token),
      body: JSON.stringify({ newPassword })
    });
    return handleResponse(res);
  },

  logout: async (token) => {
    try {
      if (token) {
        await fetch(`${API_BASE_URL}/api/auth/logout`, {
          method: 'POST',
          headers: getHeaders(token)
        });
      }
    } catch (e) {
      console.warn('Stateless logout call completed locally.', e);
    }
  }
};

export const profileService = {
  getProfile: async (token) => {
    const res = await fetch(`${API_BASE_URL}/api/profiles/me`, {
      method: 'GET',
      headers: getHeaders(token)
    });
    return handleResponse(res);
  },

  updateProfile: async (token, firstName, lastName) => {
    const res = await fetch(`${API_BASE_URL}/api/profiles/me`, {
      method: 'PUT',
      headers: getHeaders(token),
      body: JSON.stringify({ firstName, lastName })
    });
    return handleResponse(res);
  }
};

export const proteinService = {
  getProteins: async (params = {}) => {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        query.append(key, value);
      }
    });
    const url = `${API_BASE_URL}/api/proteins${query.toString() ? '?' + query.toString() : ''}`;
    const res = await fetch(url, {
      method: 'GET',
      headers: getHeaders()
    });
    return handleResponse(res);
  }
};

export const bookmarkService = {
  getBookmarks: async (token) => {
    const res = await fetch(`${API_BASE_URL}/api/bookmarks`, {
      method: 'GET',
      headers: getHeaders(token)
    });
    return handleResponse(res);
  },

  createBookmark: async (token, proteinId, proteinData, comment) => {
    const res = await fetch(`${API_BASE_URL}/api/bookmarks`, {
      method: 'POST',
      headers: getHeaders(token),
      body: JSON.stringify({ proteinId, proteinData, comment })
    });
    return handleResponse(res);
  },

  updateComment: async (token, bookmarkId, comment) => {
    const res = await fetch(`${API_BASE_URL}/api/bookmarks/${bookmarkId}/comment`, {
      method: 'PUT',
      headers: getHeaders(token),
      body: JSON.stringify({ comment })
    });
    return handleResponse(res);
  },

  deleteBookmark: async (token, bookmarkId) => {
    const res = await fetch(`${API_BASE_URL}/api/bookmarks/${bookmarkId}`, {
      method: 'DELETE',
      headers: getHeaders(token)
    });
    return handleResponse(res);
  }
};
